/*
 * Copyright (c) 2016 Fabien Siron <fabien.siron@epita.fr>
 * Copyright (c) 2016 Dmitry V. Levin <ldv@altlinux.org>
 * Copyright (c) 2016-2017 The strace developers.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "defs.h"
#include "netlink.h"
#include <linux/audit.h>
#include <linux/rtnetlink.h>
#include <linux/xfrm.h>
#include "xlat/netlink_flags.h"
#include "xlat/netlink_get_flags.h"
#include "xlat/netlink_new_flags.h"
#include "xlat/netlink_protocols.h"
#include "xlat/netlink_types.h"
#include "xlat/nl_audit_types.h"
#include "xlat/nl_netfilter_msg_types.h"
#include "xlat/nl_netfilter_subsys_ids.h"
#include "xlat/nl_route_types.h"
#include "xlat/nl_selinux_types.h"
#include "xlat/nl_sock_diag_types.h"
#include "xlat/nl_xfrm_types.h"

/*
 * Fetch a struct nlmsghdr from the given address.
 */
static bool
fetch_nlmsghdr(struct tcb *const tcp, struct nlmsghdr *const nlmsghdr,
	       const kernel_ulong_t addr, const kernel_ulong_t len)
{
	if (len < sizeof(struct nlmsghdr)) {
		printstrn(tcp, addr, len);
		return false;
	}

	if (umove_or_printaddr(tcp, addr, nlmsghdr))
		return false;

	return true;
}

enum {
	NL_FAMILY_ERROR = -1,
	NL_FAMILY_DEFAULT = -2
};

static int
get_fd_nl_family(struct tcb *const tcp, const int fd)
{
	const unsigned long inode = getfdinode(tcp, fd);
	if (!inode)
		return NL_FAMILY_ERROR;

	const char *const details = get_sockaddr_by_inode(tcp, fd, inode);
	if (!details)
		return NL_FAMILY_ERROR;

	const char *const nl_details = STR_STRIP_PREFIX(details, "NETLINK:[");
	if (nl_details == details)
		return NL_FAMILY_ERROR;

	const struct xlat *xlats = netlink_protocols;
	for (; xlats->str; ++xlats) {
		const char *name = STR_STRIP_PREFIX(xlats->str, "NETLINK_");
		if (!strncmp(nl_details, name, strlen(name)))
			return xlats->val;
	}

	if (*nl_details >= '0' && *nl_details <= '9')
		return atoi(nl_details);

	return NL_FAMILY_ERROR;
}

static void
decode_nlmsg_type_default(const struct xlat *const xlat,
			  const uint16_t type,
			  const char *const dflt)
{
	printxval(xlat, type, dflt);
}

static void
decode_nlmsg_type_generic(const struct xlat *const xlat,
			  const uint16_t type,
			  const char *const dflt)
{
	printxval(genl_families_xlat(), type, dflt);
}

static void
decode_nlmsg_type_netfilter(const struct xlat *const xlat,
			    const uint16_t type,
			    const char *const dflt)
{
	/* Reserved control nfnetlink messages first. */
	const char *const text = xlookup(nl_netfilter_msg_types, type);
	if (text) {
		tprints(text);
		return;
	}

	/*
	 * Other netfilter message types are split
	 * in two pieces: 8 bits subsystem and 8 bits type.
	 */
	const uint8_t subsys_id = (uint8_t) (type >> 8);
	const uint8_t msg_type = (uint8_t) type;

	printxval(xlat, subsys_id, dflt);

	/*
	 * The type is subsystem specific,
	 * print it in numeric format for now.
	 */
	tprintf("<<8|%#x", msg_type);
}

typedef void (*nlmsg_types_decoder_t)(const struct xlat *,
				      uint16_t type,
				      const char *dflt);

static const struct {
	const nlmsg_types_decoder_t decoder;
	const struct xlat *const xlat;
	const char *const dflt;
} nlmsg_types[] = {
	[NETLINK_AUDIT] = { NULL, nl_audit_types, "AUDIT_???" },
	[NETLINK_GENERIC] = {
		decode_nlmsg_type_generic,
		NULL,
		"GENERIC_FAMILY_???"
	},
	[NETLINK_NETFILTER] = {
		decode_nlmsg_type_netfilter,
		nl_netfilter_subsys_ids,
		"NFNL_SUBSYS_???"
	},
	[NETLINK_ROUTE] = { NULL, nl_route_types, "RTM_???" },
	[NETLINK_SELINUX] = { NULL, nl_selinux_types, "SELNL_MSG_???" },
	[NETLINK_SOCK_DIAG] = { NULL, nl_sock_diag_types, "SOCK_DIAG_???" },
	[NETLINK_XFRM] = { NULL, nl_xfrm_types, "XFRM_MSG_???" }
};

/*
 * As all valid netlink families are positive integers, use unsigned int
 * for family here to filter out NL_FAMILY_ERROR and NL_FAMILY_DEFAULT.
 */
static void
decode_nlmsg_type(const uint16_t type, const unsigned int family)
{
	nlmsg_types_decoder_t decoder = decode_nlmsg_type_default;
	const struct xlat *xlat = netlink_types;
	const char *dflt = "NLMSG_???";

	if (type != NLMSG_DONE && family < ARRAY_SIZE(nlmsg_types)) {
		if (nlmsg_types[family].decoder)
			decoder = nlmsg_types[family].decoder;
		if (nlmsg_types[family].xlat)
			xlat = nlmsg_types[family].xlat;
		if (nlmsg_types[family].dflt)
			dflt = nlmsg_types[family].dflt;
	}

	decoder(xlat, type, dflt);
}

static void
decode_nlmsg_flags(const uint16_t flags, const uint16_t type, const int family)
{
	const struct xlat *table = NULL;

	if (type == NLMSG_DONE)
		goto end;

	switch (family) {
	case NETLINK_SOCK_DIAG:
		table = netlink_get_flags;
		break;
	case NETLINK_ROUTE:
		if (type == RTM_DELACTION) {
			table = netlink_get_flags;
			break;
		}
		switch (type & 3) {
		case  0:
			table = netlink_new_flags;
			break;
		case  2:
			table = netlink_get_flags;
			break;
		}
		break;
	case NETLINK_XFRM:
		switch (type) {
		case XFRM_MSG_NEWSA:
		case XFRM_MSG_NEWPOLICY:
		case XFRM_MSG_NEWAE:
		case XFRM_MSG_NEWSADINFO:
		case XFRM_MSG_NEWSPDINFO:
			table = netlink_new_flags;
			break;

		case XFRM_MSG_GETSA:
		case XFRM_MSG_GETPOLICY:
		case XFRM_MSG_GETAE:
		case XFRM_MSG_GETSADINFO:
		case XFRM_MSG_GETSPDINFO:
			table = netlink_get_flags;
			break;
		}
		break;
	}

end:
	printflags_ex(flags, "NLM_F_???", netlink_flags, table, NULL);
}

static int
print_nlmsghdr(struct tcb *tcp,
	       const int fd,
	       int family,
	       const struct nlmsghdr *const nlmsghdr)
{
	/* print the whole structure regardless of its nlmsg_len */

	tprintf("{len=%u, type=", nlmsghdr->nlmsg_len);

	const int hdr_family = (nlmsghdr->nlmsg_type < NLMSG_MIN_TYPE
				&& nlmsghdr->nlmsg_type != NLMSG_DONE)
			       ? NL_FAMILY_DEFAULT
			       : (family != NL_FAMILY_DEFAULT
				  ? family : get_fd_nl_family(tcp, fd));

	decode_nlmsg_type(nlmsghdr->nlmsg_type, hdr_family);

	tprints(", flags=");
	decode_nlmsg_flags(nlmsghdr->nlmsg_flags,
			   nlmsghdr->nlmsg_type, hdr_family);

	tprintf(", seq=%u, pid=%u}", nlmsghdr->nlmsg_seq,
		nlmsghdr->nlmsg_pid);

	return family != NL_FAMILY_DEFAULT ? family : hdr_family;
}

static void
decode_nlmsghdr_with_payload(struct tcb *const tcp,
			     const int fd,
			     int family,
			     const struct nlmsghdr *const nlmsghdr,
			     const kernel_ulong_t addr,
			     const kernel_ulong_t len);

static void
decode_nlmsgerr(struct tcb *const tcp,
		const int fd,
		const int family,
		kernel_ulong_t addr,
		kernel_ulong_t len)
{
	struct nlmsgerr err;

	if (len < sizeof(err.error)) {
		printstrn(tcp, addr, len);
		return;
	}

	if (umove_or_printaddr(tcp, addr, &err.error))
		return;

	tprints("{error=");
	if (err.error < 0 && (unsigned) -err.error < nerrnos) {
		tprintf("-%s", errnoent[-err.error]);
	} else {
		tprintf("%d", err.error);
	}

	addr += offsetof(struct nlmsgerr, msg);
	len -= offsetof(struct nlmsgerr, msg);

	if (len) {
		tprints(", msg=");
		if (fetch_nlmsghdr(tcp, &err.msg, addr, len)) {
			decode_nlmsghdr_with_payload(tcp, fd, family,
						     &err.msg, addr, len);
		}
	}

	tprints("}");
}

static const netlink_decoder_t netlink_decoders[] = {
	[NETLINK_SOCK_DIAG] = decode_netlink_sock_diag
};

static void
decode_payload(struct tcb *const tcp,
	       const int fd,
	       const int family,
	       const struct nlmsghdr *const nlmsghdr,
	       const kernel_ulong_t addr,
	       const kernel_ulong_t len)
{
	if (nlmsghdr->nlmsg_type == NLMSG_ERROR) {
		decode_nlmsgerr(tcp, fd, family, addr, len);
		return;
	}

	if ((unsigned int) family < ARRAY_SIZE(netlink_decoders)
	    && netlink_decoders[family]
	    && netlink_decoders[family](tcp, nlmsghdr, addr, len)) {
		return;
	}

	if (nlmsghdr->nlmsg_type == NLMSG_DONE && len == sizeof(int)) {
		int num;

		if (!umove_or_printaddr(tcp, addr, &num))
			tprintf("%d", num);
		return;
	}

	printstrn(tcp, addr, len);
}

static void
decode_nlmsghdr_with_payload(struct tcb *const tcp,
			     const int fd,
			     int family,
			     const struct nlmsghdr *const nlmsghdr,
			     const kernel_ulong_t addr,
			     const kernel_ulong_t len)
{
	const unsigned int nlmsg_len =
		nlmsghdr->nlmsg_len > len ? len : nlmsghdr->nlmsg_len;

	if (nlmsg_len > NLMSG_HDRLEN)
		tprints("{");

	family = print_nlmsghdr(tcp, fd, family, nlmsghdr);

	if (nlmsg_len > NLMSG_HDRLEN) {
		tprints(", ");
		decode_payload(tcp, fd, family, nlmsghdr, addr + NLMSG_HDRLEN,
						     nlmsg_len - NLMSG_HDRLEN);
		tprints("}");
	}
}

void
decode_netlink(struct tcb *const tcp,
	       const int fd,
	       kernel_ulong_t addr,
	       kernel_ulong_t len)
{
	struct nlmsghdr nlmsghdr;
	bool print_array = false;
	unsigned int elt;

	for (elt = 0; fetch_nlmsghdr(tcp, &nlmsghdr, addr, len); elt++) {
		if (abbrev(tcp) && elt == max_strlen) {
			tprints("...");
			break;
		}

		unsigned int nlmsg_len = NLMSG_ALIGN(nlmsghdr.nlmsg_len);
		kernel_ulong_t next_addr = 0;
		kernel_ulong_t next_len = 0;

		if (nlmsghdr.nlmsg_len >= NLMSG_HDRLEN) {
			next_len = (len >= nlmsg_len) ? len - nlmsg_len : 0;

			if (next_len && addr + nlmsg_len > addr)
				next_addr = addr + nlmsg_len;
		}

		if (!print_array && next_addr) {
			tprints("[");
			print_array = true;
		}

		decode_nlmsghdr_with_payload(tcp, fd, NL_FAMILY_DEFAULT,
					     &nlmsghdr, addr, len);

		if (!next_addr)
			break;

		tprints(", ");
		addr = next_addr;
		len = next_len;
	}

	if (print_array) {
		tprints("]");
	}
}
