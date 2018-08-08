/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#define _BSD_SOURCE
#define _DEFAULT_SOURCE
#define _GNU_SOURCE

#include <asm/unistd.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <linux/capability.h>
#include <pwd.h>
#include <sched.h>
#include <signal.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/capability.h>
#include <sys/mount.h>
#include <sys/param.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <syscall.h>
#include <unistd.h>

#include "libminijail.h"
#include "libminijail-private.h"

#include "signal_handler.h"
#include "syscall_filter.h"
#include "syscall_wrapper.h"
#include "system.h"
#include "util.h"

/* Until these are reliably available in linux/prctl.h. */
#ifndef PR_ALT_SYSCALL
# define PR_ALT_SYSCALL 0x43724f53
#endif

/* Seccomp filter related flags. */
#ifndef PR_SET_NO_NEW_PRIVS
# define PR_SET_NO_NEW_PRIVS 38
#endif

#ifndef SECCOMP_MODE_FILTER
#define SECCOMP_MODE_FILTER 2 /* Uses user-supplied filter. */
#endif

#ifndef SECCOMP_SET_MODE_STRICT
# define SECCOMP_SET_MODE_STRICT 0
#endif
#ifndef SECCOMP_SET_MODE_FILTER
# define SECCOMP_SET_MODE_FILTER 1
#endif

#ifndef SECCOMP_FILTER_FLAG_TSYNC
# define SECCOMP_FILTER_FLAG_TSYNC 1
#endif
/* End seccomp filter related flags. */

/* New cgroup namespace might not be in linux-headers yet. */
#ifndef CLONE_NEWCGROUP
# define CLONE_NEWCGROUP 0x02000000
#endif

#define MAX_CGROUPS 10 /* 10 different controllers supported by Linux. */

#define MAX_RLIMITS 32 /* Currently there are 15 supported by Linux. */

/* Keyctl commands. */
#define KEYCTL_JOIN_SESSION_KEYRING 1

struct minijail_rlimit {
	int type;
	uint32_t cur;
	uint32_t max;
};

struct mountpoint {
	char *src;
	char *dest;
	char *type;
	char *data;
	int has_data;
	unsigned long flags;
	struct mountpoint *next;
};

struct minijail {
	/*
	 * WARNING: if you add a flag here you need to make sure it's
	 * accounted for in minijail_pre{enter|exec}() below.
	 */
	struct {
		int uid : 1;
		int gid : 1;
		int inherit_suppl_gids : 1;
		int set_suppl_gids : 1;
		int keep_suppl_gids : 1;
		int use_caps : 1;
		int capbset_drop : 1;
		int set_ambient_caps : 1;
		int vfs : 1;
		int enter_vfs : 1;
		int skip_remount_private : 1;
		int pids : 1;
		int ipc : 1;
		int uts : 1;
		int net : 1;
		int enter_net : 1;
		int ns_cgroups : 1;
		int userns : 1;
		int disable_setgroups : 1;
		int seccomp : 1;
		int remount_proc_ro : 1;
		int no_new_privs : 1;
		int seccomp_filter : 1;
		int seccomp_filter_tsync : 1;
		int seccomp_filter_logging : 1;
		int chroot : 1;
		int pivot_root : 1;
		int mount_tmp : 1;
		int do_init : 1;
		int pid_file : 1;
		int cgroups : 1;
		int alt_syscall : 1;
		int reset_signal_mask : 1;
		int close_open_fds : 1;
		int new_session_keyring : 1;
		int forward_signals : 1;
	} flags;
	uid_t uid;
	gid_t gid;
	gid_t usergid;
	char *user;
	size_t suppl_gid_count;
	gid_t *suppl_gid_list;
	uint64_t caps;
	uint64_t cap_bset;
	pid_t initpid;
	int mountns_fd;
	int netns_fd;
	char *chrootdir;
	char *pid_file_path;
	char *uidmap;
	char *gidmap;
	char *hostname;
	size_t filter_len;
	struct sock_fprog *filter_prog;
	char *alt_syscall_table;
	struct mountpoint *mounts_head;
	struct mountpoint *mounts_tail;
	size_t mounts_count;
	size_t tmpfs_size;
	char *cgroups[MAX_CGROUPS];
	size_t cgroup_count;
	struct minijail_rlimit rlimits[MAX_RLIMITS];
	size_t rlimit_count;
	uint64_t securebits_skip_mask;
};

/*
 * Strip out flags meant for the parent.
 * We keep things that are not inherited across execve(2) (e.g. capabilities),
 * or are easier to set after execve(2) (e.g. seccomp filters).
 */
void minijail_preenter(struct minijail *j)
{
	j->flags.vfs = 0;
	j->flags.enter_vfs = 0;
	j->flags.skip_remount_private = 0;
	j->flags.remount_proc_ro = 0;
	j->flags.pids = 0;
	j->flags.do_init = 0;
	j->flags.pid_file = 0;
	j->flags.cgroups = 0;
	j->flags.forward_signals = 0;
}

/*
 * Strip out flags meant for the child.
 * We keep things that are inherited across execve(2).
 */
void minijail_preexec(struct minijail *j)
{
	int vfs = j->flags.vfs;
	int enter_vfs = j->flags.enter_vfs;
	int skip_remount_private = j->flags.skip_remount_private;
	int remount_proc_ro = j->flags.remount_proc_ro;
	int userns = j->flags.userns;
	if (j->user)
		free(j->user);
	j->user = NULL;
	if (j->suppl_gid_list)
		free(j->suppl_gid_list);
	j->suppl_gid_list = NULL;
	memset(&j->flags, 0, sizeof(j->flags));
	/* Now restore anything we meant to keep. */
	j->flags.vfs = vfs;
	j->flags.enter_vfs = enter_vfs;
	j->flags.skip_remount_private = skip_remount_private;
	j->flags.remount_proc_ro = remount_proc_ro;
	j->flags.userns = userns;
	/* Note, |pids| will already have been used before this call. */
}

/* Minijail API. */

struct minijail API *minijail_new(void)
{
	return calloc(1, sizeof(struct minijail));
}

void API minijail_change_uid(struct minijail *j, uid_t uid)
{
	if (uid == 0)
		die("useless change to uid 0");
	j->uid = uid;
	j->flags.uid = 1;
}

void API minijail_change_gid(struct minijail *j, gid_t gid)
{
	if (gid == 0)
		die("useless change to gid 0");
	j->gid = gid;
	j->flags.gid = 1;
}

void API minijail_set_supplementary_gids(struct minijail *j, size_t size,
					 const gid_t *list)
{
	size_t i;

	if (j->flags.inherit_suppl_gids)
		die("cannot inherit *and* set supplementary groups");
	if (j->flags.keep_suppl_gids)
		die("cannot keep *and* set supplementary groups");

	if (size == 0) {
		/* Clear supplementary groups. */
		j->suppl_gid_list = NULL;
		j->suppl_gid_count = 0;
		j->flags.set_suppl_gids = 1;
		return;
	}

	/* Copy the gid_t array. */
	j->suppl_gid_list = calloc(size, sizeof(gid_t));
	if (!j->suppl_gid_list) {
		die("failed to allocate internal supplementary group array");
	}
	for (i = 0; i < size; i++) {
		j->suppl_gid_list[i] = list[i];
	}
	j->suppl_gid_count = size;
	j->flags.set_suppl_gids = 1;
}

void API minijail_keep_supplementary_gids(struct minijail *j) {
	j->flags.keep_suppl_gids = 1;
}

int API minijail_change_user(struct minijail *j, const char *user)
{
	char *buf = NULL;
	struct passwd pw;
	struct passwd *ppw = NULL;
	ssize_t sz = sysconf(_SC_GETPW_R_SIZE_MAX);
	if (sz == -1)
		sz = 65536;	/* your guess is as good as mine... */

	/*
	 * sysconf(_SC_GETPW_R_SIZE_MAX), under glibc, is documented to return
	 * the maximum needed size of the buffer, so we don't have to search.
	 */
	buf = malloc(sz);
	if (!buf)
		return -ENOMEM;
	getpwnam_r(user, &pw, buf, sz, &ppw);
	/*
	 * We're safe to free the buffer here. The strings inside |pw| point
	 * inside |buf|, but we don't use any of them; this leaves the pointers
	 * dangling but it's safe. |ppw| points at |pw| if getpwnam_r(3)
	 * succeeded.
	 */
	free(buf);
	/* getpwnam_r(3) does *not* set errno when |ppw| is NULL. */
	if (!ppw)
		return -1;
	minijail_change_uid(j, ppw->pw_uid);
	j->user = strdup(user);
	if (!j->user)
		return -ENOMEM;
	j->usergid = ppw->pw_gid;
	return 0;
}

int API minijail_change_group(struct minijail *j, const char *group)
{
	char *buf = NULL;
	struct group gr;
	struct group *pgr = NULL;
	ssize_t sz = sysconf(_SC_GETGR_R_SIZE_MAX);
	if (sz == -1)
		sz = 65536;	/* and mine is as good as yours, really */

	/*
	 * sysconf(_SC_GETGR_R_SIZE_MAX), under glibc, is documented to return
	 * the maximum needed size of the buffer, so we don't have to search.
	 */
	buf = malloc(sz);
	if (!buf)
		return -ENOMEM;
	getgrnam_r(group, &gr, buf, sz, &pgr);
	/*
	 * We're safe to free the buffer here. The strings inside gr point
	 * inside buf, but we don't use any of them; this leaves the pointers
	 * dangling but it's safe. pgr points at gr if getgrnam_r succeeded.
	 */
	free(buf);
	/* getgrnam_r(3) does *not* set errno when |pgr| is NULL. */
	if (!pgr)
		return -1;
	minijail_change_gid(j, pgr->gr_gid);
	return 0;
}

void API minijail_use_seccomp(struct minijail *j)
{
	j->flags.seccomp = 1;
}

void API minijail_no_new_privs(struct minijail *j)
{
	j->flags.no_new_privs = 1;
}

void API minijail_use_seccomp_filter(struct minijail *j)
{
	j->flags.seccomp_filter = 1;
}

void API minijail_set_seccomp_filter_tsync(struct minijail *j)
{
	if (j->filter_len > 0 && j->filter_prog != NULL) {
		die("minijail_set_seccomp_filter_tsync() must be called "
		    "before minijail_parse_seccomp_filters()");
	}
	j->flags.seccomp_filter_tsync = 1;
}

void API minijail_log_seccomp_filter_failures(struct minijail *j)
{
	if (j->filter_len > 0 && j->filter_prog != NULL) {
		die("minijail_log_seccomp_filter_failures() must be called "
		    "before minijail_parse_seccomp_filters()");
	}
	j->flags.seccomp_filter_logging = 1;
}

void API minijail_use_caps(struct minijail *j, uint64_t capmask)
{
	/*
	 * 'minijail_use_caps' configures a runtime-capabilities-only
	 * environment, including a bounding set matching the thread's runtime
	 * (permitted|inheritable|effective) sets.
	 * Therefore, it will override any existing bounding set configurations
	 * since the latter would allow gaining extra runtime capabilities from
	 * file capabilities.
	 */
	if (j->flags.capbset_drop) {
		warn("overriding bounding set configuration");
		j->cap_bset = 0;
		j->flags.capbset_drop = 0;
	}
	j->caps = capmask;
	j->flags.use_caps = 1;
}

void API minijail_capbset_drop(struct minijail *j, uint64_t capmask)
{
	if (j->flags.use_caps) {
		/*
		 * 'minijail_use_caps' will have already configured a capability
		 * bounding set matching the (permitted|inheritable|effective)
		 * sets. Abort if the user tries to configure a separate
		 * bounding set. 'minijail_capbset_drop' and 'minijail_use_caps'
		 * are mutually exclusive.
		 */
		die("runtime capabilities already configured, can't drop "
		    "bounding set separately");
	}
	j->cap_bset = capmask;
	j->flags.capbset_drop = 1;
}

void API minijail_set_ambient_caps(struct minijail *j)
{
	j->flags.set_ambient_caps = 1;
}

void API minijail_reset_signal_mask(struct minijail *j)
{
	j->flags.reset_signal_mask = 1;
}

void API minijail_namespace_vfs(struct minijail *j)
{
	j->flags.vfs = 1;
}

void API minijail_namespace_enter_vfs(struct minijail *j, const char *ns_path)
{
	int ns_fd = open(ns_path, O_RDONLY | O_CLOEXEC);
	if (ns_fd < 0) {
		pdie("failed to open namespace '%s'", ns_path);
	}
	j->mountns_fd = ns_fd;
	j->flags.enter_vfs = 1;
}

void API minijail_new_session_keyring(struct minijail *j)
{
	j->flags.new_session_keyring = 1;
}

void API minijail_skip_setting_securebits(struct minijail *j,
					  uint64_t securebits_skip_mask)
{
	j->securebits_skip_mask = securebits_skip_mask;
}

void API minijail_skip_remount_private(struct minijail *j)
{
	j->flags.skip_remount_private = 1;
}

void API minijail_namespace_pids(struct minijail *j)
{
	j->flags.vfs = 1;
	j->flags.remount_proc_ro = 1;
	j->flags.pids = 1;
	j->flags.do_init = 1;
}

void API minijail_namespace_ipc(struct minijail *j)
{
	j->flags.ipc = 1;
}

void API minijail_namespace_uts(struct minijail *j)
{
	j->flags.uts = 1;
}

int API minijail_namespace_set_hostname(struct minijail *j, const char *name)
{
	if (j->hostname)
		return -EINVAL;
	minijail_namespace_uts(j);
	j->hostname = strdup(name);
	if (!j->hostname)
		return -ENOMEM;
	return 0;
}

void API minijail_namespace_net(struct minijail *j)
{
	j->flags.net = 1;
}

void API minijail_namespace_enter_net(struct minijail *j, const char *ns_path)
{
	int ns_fd = open(ns_path, O_RDONLY | O_CLOEXEC);
	if (ns_fd < 0) {
		pdie("failed to open namespace '%s'", ns_path);
	}
	j->netns_fd = ns_fd;
	j->flags.enter_net = 1;
}

void API minijail_namespace_cgroups(struct minijail *j)
{
	j->flags.ns_cgroups = 1;
}

void API minijail_close_open_fds(struct minijail *j)
{
	j->flags.close_open_fds = 1;
}

void API minijail_remount_proc_readonly(struct minijail *j)
{
	j->flags.vfs = 1;
	j->flags.remount_proc_ro = 1;
}

void API minijail_namespace_user(struct minijail *j)
{
	j->flags.userns = 1;
}

void API minijail_namespace_user_disable_setgroups(struct minijail *j)
{
	j->flags.disable_setgroups = 1;
}

int API minijail_uidmap(struct minijail *j, const char *uidmap)
{
	j->uidmap = strdup(uidmap);
	if (!j->uidmap)
		return -ENOMEM;
	char *ch;
	for (ch = j->uidmap; *ch; ch++) {
		if (*ch == ',')
			*ch = '\n';
	}
	return 0;
}

int API minijail_gidmap(struct minijail *j, const char *gidmap)
{
	j->gidmap = strdup(gidmap);
	if (!j->gidmap)
		return -ENOMEM;
	char *ch;
	for (ch = j->gidmap; *ch; ch++) {
		if (*ch == ',')
			*ch = '\n';
	}
	return 0;
}

void API minijail_inherit_usergroups(struct minijail *j)
{
	j->flags.inherit_suppl_gids = 1;
}

void API minijail_run_as_init(struct minijail *j)
{
	/*
	 * Since the jailed program will become 'init' in the new PID namespace,
	 * Minijail does not need to fork an 'init' process.
	 */
	j->flags.do_init = 0;
}

int API minijail_enter_chroot(struct minijail *j, const char *dir)
{
	if (j->chrootdir)
		return -EINVAL;
	j->chrootdir = strdup(dir);
	if (!j->chrootdir)
		return -ENOMEM;
	j->flags.chroot = 1;
	return 0;
}

int API minijail_enter_pivot_root(struct minijail *j, const char *dir)
{
	if (j->chrootdir)
		return -EINVAL;
	j->chrootdir = strdup(dir);
	if (!j->chrootdir)
		return -ENOMEM;
	j->flags.pivot_root = 1;
	return 0;
}

char API *minijail_get_original_path(struct minijail *j,
				     const char *path_inside_chroot)
{
	struct mountpoint *b;

	b = j->mounts_head;
	while (b) {
		/*
		 * If |path_inside_chroot| is the exact destination of a
		 * mount, then the original path is exactly the source of
		 * the mount.
		 *  for example: "-b /some/path/exe,/chroot/path/exe"
		 *    mount source = /some/path/exe, mount dest =
		 *    /chroot/path/exe Then when getting the original path of
		 *    "/chroot/path/exe", the source of that mount,
		 *    "/some/path/exe" is what should be returned.
		 */
		if (!strcmp(b->dest, path_inside_chroot))
			return strdup(b->src);

		/*
		 * If |path_inside_chroot| is within the destination path of a
		 * mount, take the suffix of the chroot path relative to the
		 * mount destination path, and append it to the mount source
		 * path.
		 */
		if (!strncmp(b->dest, path_inside_chroot, strlen(b->dest))) {
			const char *relative_path =
				path_inside_chroot + strlen(b->dest);
			return path_join(b->src, relative_path);
		}
		b = b->next;
	}

	/* If there is a chroot path, append |path_inside_chroot| to that. */
	if (j->chrootdir)
		return path_join(j->chrootdir, path_inside_chroot);

	/* No chroot, so the path outside is the same as it is inside. */
	return strdup(path_inside_chroot);
}

size_t minijail_get_tmpfs_size(const struct minijail *j)
{
	return j->tmpfs_size;
}

void API minijail_mount_tmp(struct minijail *j)
{
	minijail_mount_tmp_size(j, 64 * 1024 * 1024);
}

void API minijail_mount_tmp_size(struct minijail *j, size_t size)
{
	j->tmpfs_size = size;
	j->flags.mount_tmp = 1;
}

int API minijail_write_pid_file(struct minijail *j, const char *path)
{
	j->pid_file_path = strdup(path);
	if (!j->pid_file_path)
		return -ENOMEM;
	j->flags.pid_file = 1;
	return 0;
}

int API minijail_add_to_cgroup(struct minijail *j, const char *path)
{
	if (j->cgroup_count >= MAX_CGROUPS)
		return -ENOMEM;
	j->cgroups[j->cgroup_count] = strdup(path);
	if (!j->cgroups[j->cgroup_count])
		return -ENOMEM;
	j->cgroup_count++;
	j->flags.cgroups = 1;
	return 0;
}

int API minijail_rlimit(struct minijail *j, int type, uint32_t cur,
			uint32_t max)
{
	size_t i;

	if (j->rlimit_count >= MAX_RLIMITS)
		return -ENOMEM;
	/* It's an error if the caller sets the same rlimit multiple times. */
	for (i = 0; i < j->rlimit_count; i++) {
		if (j->rlimits[i].type == type)
			return -EEXIST;
	}

	j->rlimits[j->rlimit_count].type = type;
	j->rlimits[j->rlimit_count].cur = cur;
	j->rlimits[j->rlimit_count].max = max;
	j->rlimit_count++;
	return 0;
}

int API minijail_forward_signals(struct minijail *j)
{
	j->flags.forward_signals = 1;
	return 0;
}

int API minijail_mount_with_data(struct minijail *j, const char *src,
				 const char *dest, const char *type,
				 unsigned long flags, const char *data)
{
	struct mountpoint *m;

	if (*dest != '/')
		return -EINVAL;
	m = calloc(1, sizeof(*m));
	if (!m)
		return -ENOMEM;
	m->dest = strdup(dest);
	if (!m->dest)
		goto error;
	m->src = strdup(src);
	if (!m->src)
		goto error;
	m->type = strdup(type);
	if (!m->type)
		goto error;
	if (data) {
		m->data = strdup(data);
		if (!m->data)
			goto error;
		m->has_data = 1;
	}
	m->flags = flags;

	info("mount %s -> %s type '%s'", src, dest, type);

	/*
	 * Force vfs namespacing so the mounts don't leak out into the
	 * containing vfs namespace.
	 */
	minijail_namespace_vfs(j);

	if (j->mounts_tail)
		j->mounts_tail->next = m;
	else
		j->mounts_head = m;
	j->mounts_tail = m;
	j->mounts_count++;

	return 0;

error:
	free(m->type);
	free(m->src);
	free(m->dest);
	free(m);
	return -ENOMEM;
}

int API minijail_mount(struct minijail *j, const char *src, const char *dest,
		       const char *type, unsigned long flags)
{
	return minijail_mount_with_data(j, src, dest, type, flags, NULL);
}

int API minijail_bind(struct minijail *j, const char *src, const char *dest,
		      int writeable)
{
	unsigned long flags = MS_BIND;

	if (!writeable)
		flags |= MS_RDONLY;

	return minijail_mount(j, src, dest, "", flags);
}

static void clear_seccomp_options(struct minijail *j)
{
	j->flags.seccomp_filter = 0;
	j->flags.seccomp_filter_tsync = 0;
	j->flags.seccomp_filter_logging = 0;
	j->filter_len = 0;
	j->filter_prog = NULL;
	j->flags.no_new_privs = 0;
}

static int seccomp_should_parse_filters(struct minijail *j)
{
	if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, NULL) == -1) {
		/*
		 * |errno| will be set to EINVAL when seccomp has not been
		 * compiled into the kernel. On certain platforms and kernel
		 * versions this is not a fatal failure. In that case, and only
		 * in that case, disable seccomp and skip loading the filters.
		 */
		if ((errno == EINVAL) && seccomp_can_softfail()) {
			warn("not loading seccomp filters, seccomp filter not "
			     "supported");
			clear_seccomp_options(j);
			return 0;
		}
		/*
		 * If |errno| != EINVAL or seccomp_can_softfail() is false,
		 * we can proceed. Worst case scenario minijail_enter() will
		 * abort() if seccomp fails.
		 */
	}
	if (j->flags.seccomp_filter_tsync) {
		/* Are the seccomp(2) syscall and the TSYNC option supported? */
		if (sys_seccomp(SECCOMP_SET_MODE_FILTER,
				SECCOMP_FILTER_FLAG_TSYNC, NULL) == -1) {
			int saved_errno = errno;
			if (saved_errno == ENOSYS && seccomp_can_softfail()) {
				warn("seccomp(2) syscall not supported");
				clear_seccomp_options(j);
				return 0;
			} else if (saved_errno == EINVAL &&
				   seccomp_can_softfail()) {
				warn(
				    "seccomp filter thread sync not supported");
				clear_seccomp_options(j);
				return 0;
			}
			/*
			 * Similar logic here. If seccomp_can_softfail() is
			 * false, or |errno| != ENOSYS, or |errno| != EINVAL,
			 * we can proceed. Worst case scenario minijail_enter()
			 * will abort() if seccomp or TSYNC fail.
			 */
		}
	}
	return 1;
}

static int parse_seccomp_filters(struct minijail *j, FILE *policy_file)
{
	struct sock_fprog *fprog = malloc(sizeof(struct sock_fprog));
	int use_ret_trap =
	    j->flags.seccomp_filter_tsync || j->flags.seccomp_filter_logging;
	int allow_logging = j->flags.seccomp_filter_logging;

	if (compile_filter(policy_file, fprog, use_ret_trap, allow_logging)) {
		free(fprog);
		return -1;
	}

	j->filter_len = fprog->len;
	j->filter_prog = fprog;
	return 0;
}

void API minijail_parse_seccomp_filters(struct minijail *j, const char *path)
{
	if (!seccomp_should_parse_filters(j))
		return;

	FILE *file = fopen(path, "r");
	if (!file) {
		pdie("failed to open seccomp filter file '%s'", path);
	}

	if (parse_seccomp_filters(j, file) != 0) {
		die("failed to compile seccomp filter BPF program in '%s'",
		    path);
	}
	fclose(file);
}

void API minijail_parse_seccomp_filters_from_fd(struct minijail *j, int fd)
{
	if (!seccomp_should_parse_filters(j))
		return;

	FILE *file = fdopen(fd, "r");
	if (!file) {
		pdie("failed to associate stream with fd %d", fd);
	}

	if (parse_seccomp_filters(j, file) != 0) {
		die("failed to compile seccomp filter BPF program from fd %d",
		    fd);
	}
	fclose(file);
}

int API minijail_use_alt_syscall(struct minijail *j, const char *table)
{
	j->alt_syscall_table = strdup(table);
	if (!j->alt_syscall_table)
		return -ENOMEM;
	j->flags.alt_syscall = 1;
	return 0;
}

struct marshal_state {
	size_t available;
	size_t total;
	char *buf;
};

void marshal_state_init(struct marshal_state *state, char *buf,
			size_t available)
{
	state->available = available;
	state->buf = buf;
	state->total = 0;
}

void marshal_append(struct marshal_state *state, void *src, size_t length)
{
	size_t copy_len = MIN(state->available, length);

	/* Up to |available| will be written. */
	if (copy_len) {
		memcpy(state->buf, src, copy_len);
		state->buf += copy_len;
		state->available -= copy_len;
	}
	/* |total| will contain the expected length. */
	state->total += length;
}

void marshal_mount(struct marshal_state *state, const struct mountpoint *m)
{
	marshal_append(state, m->src, strlen(m->src) + 1);
	marshal_append(state, m->dest, strlen(m->dest) + 1);
	marshal_append(state, m->type, strlen(m->type) + 1);
	marshal_append(state, (char *)&m->has_data, sizeof(m->has_data));
	if (m->has_data)
		marshal_append(state, m->data, strlen(m->data) + 1);
	marshal_append(state, (char *)&m->flags, sizeof(m->flags));
}

void minijail_marshal_helper(struct marshal_state *state,
			     const struct minijail *j)
{
	struct mountpoint *m = NULL;
	size_t i;

	marshal_append(state, (char *)j, sizeof(*j));
	if (j->user)
		marshal_append(state, j->user, strlen(j->user) + 1);
	if (j->suppl_gid_list) {
		marshal_append(state, j->suppl_gid_list,
			       j->suppl_gid_count * sizeof(gid_t));
	}
	if (j->chrootdir)
		marshal_append(state, j->chrootdir, strlen(j->chrootdir) + 1);
	if (j->hostname)
		marshal_append(state, j->hostname, strlen(j->hostname) + 1);
	if (j->alt_syscall_table) {
		marshal_append(state, j->alt_syscall_table,
			       strlen(j->alt_syscall_table) + 1);
	}
	if (j->flags.seccomp_filter && j->filter_prog) {
		struct sock_fprog *fp = j->filter_prog;
		marshal_append(state, (char *)fp->filter,
			       fp->len * sizeof(struct sock_filter));
	}
	for (m = j->mounts_head; m; m = m->next) {
		marshal_mount(state, m);
	}
	for (i = 0; i < j->cgroup_count; ++i)
		marshal_append(state, j->cgroups[i], strlen(j->cgroups[i]) + 1);
}

size_t API minijail_size(const struct minijail *j)
{
	struct marshal_state state;
	marshal_state_init(&state, NULL, 0);
	minijail_marshal_helper(&state, j);
	return state.total;
}

int minijail_marshal(const struct minijail *j, char *buf, size_t available)
{
	struct marshal_state state;
	marshal_state_init(&state, buf, available);
	minijail_marshal_helper(&state, j);
	return (state.total > available);
}

int minijail_unmarshal(struct minijail *j, char *serialized, size_t length)
{
	size_t i;
	size_t count;
	int ret = -EINVAL;

	if (length < sizeof(*j))
		goto out;
	memcpy((void *)j, serialized, sizeof(*j));
	serialized += sizeof(*j);
	length -= sizeof(*j);

	/* Potentially stale pointers not used as signals. */
	j->pid_file_path = NULL;
	j->uidmap = NULL;
	j->gidmap = NULL;
	j->mounts_head = NULL;
	j->mounts_tail = NULL;
	j->filter_prog = NULL;

	if (j->user) {		/* stale pointer */
		char *user = consumestr(&serialized, &length);
		if (!user)
			goto clear_pointers;
		j->user = strdup(user);
		if (!j->user)
			goto clear_pointers;
	}

	if (j->suppl_gid_list) {	/* stale pointer */
		if (j->suppl_gid_count > NGROUPS_MAX) {
			goto bad_gid_list;
		}
		size_t gid_list_size = j->suppl_gid_count * sizeof(gid_t);
		void *gid_list_bytes =
		    consumebytes(gid_list_size, &serialized, &length);
		if (!gid_list_bytes)
			goto bad_gid_list;

		j->suppl_gid_list = calloc(j->suppl_gid_count, sizeof(gid_t));
		if (!j->suppl_gid_list)
			goto bad_gid_list;

		memcpy(j->suppl_gid_list, gid_list_bytes, gid_list_size);
	}

	if (j->chrootdir) {	/* stale pointer */
		char *chrootdir = consumestr(&serialized, &length);
		if (!chrootdir)
			goto bad_chrootdir;
		j->chrootdir = strdup(chrootdir);
		if (!j->chrootdir)
			goto bad_chrootdir;
	}

	if (j->hostname) {	/* stale pointer */
		char *hostname = consumestr(&serialized, &length);
		if (!hostname)
			goto bad_hostname;
		j->hostname = strdup(hostname);
		if (!j->hostname)
			goto bad_hostname;
	}

	if (j->alt_syscall_table) {	/* stale pointer */
		char *alt_syscall_table = consumestr(&serialized, &length);
		if (!alt_syscall_table)
			goto bad_syscall_table;
		j->alt_syscall_table = strdup(alt_syscall_table);
		if (!j->alt_syscall_table)
			goto bad_syscall_table;
	}

	if (j->flags.seccomp_filter && j->filter_len > 0) {
		size_t ninstrs = j->filter_len;
		if (ninstrs > (SIZE_MAX / sizeof(struct sock_filter)) ||
		    ninstrs > USHRT_MAX)
			goto bad_filters;

		size_t program_len = ninstrs * sizeof(struct sock_filter);
		void *program = consumebytes(program_len, &serialized, &length);
		if (!program)
			goto bad_filters;

		j->filter_prog = malloc(sizeof(struct sock_fprog));
		if (!j->filter_prog)
			goto bad_filters;

		j->filter_prog->len = ninstrs;
		j->filter_prog->filter = malloc(program_len);
		if (!j->filter_prog->filter)
			goto bad_filter_prog_instrs;

		memcpy(j->filter_prog->filter, program, program_len);
	}

	count = j->mounts_count;
	j->mounts_count = 0;
	for (i = 0; i < count; ++i) {
		unsigned long *flags;
		int *has_data;
		const char *dest;
		const char *type;
		const char *data = NULL;
		const char *src = consumestr(&serialized, &length);
		if (!src)
			goto bad_mounts;
		dest = consumestr(&serialized, &length);
		if (!dest)
			goto bad_mounts;
		type = consumestr(&serialized, &length);
		if (!type)
			goto bad_mounts;
		has_data = consumebytes(sizeof(*has_data), &serialized,
					&length);
		if (!has_data)
			goto bad_mounts;
		if (*has_data) {
			data = consumestr(&serialized, &length);
			if (!data)
				goto bad_mounts;
		}
		flags = consumebytes(sizeof(*flags), &serialized, &length);
		if (!flags)
			goto bad_mounts;
		if (minijail_mount_with_data(j, src, dest, type, *flags, data))
			goto bad_mounts;
	}

	count = j->cgroup_count;
	j->cgroup_count = 0;
	for (i = 0; i < count; ++i) {
		char *cgroup = consumestr(&serialized, &length);
		if (!cgroup)
			goto bad_cgroups;
		j->cgroups[i] = strdup(cgroup);
		if (!j->cgroups[i])
			goto bad_cgroups;
		++j->cgroup_count;
	}

	return 0;

bad_cgroups:
	while (j->mounts_head) {
		struct mountpoint *m = j->mounts_head;
		j->mounts_head = j->mounts_head->next;
		free(m->data);
		free(m->type);
		free(m->dest);
		free(m->src);
		free(m);
	}
	for (i = 0; i < j->cgroup_count; ++i)
		free(j->cgroups[i]);
bad_mounts:
	if (j->flags.seccomp_filter && j->filter_len > 0) {
		free(j->filter_prog->filter);
		free(j->filter_prog);
	}
bad_filter_prog_instrs:
	if (j->filter_prog)
		free(j->filter_prog);
bad_filters:
	if (j->alt_syscall_table)
		free(j->alt_syscall_table);
bad_syscall_table:
	if (j->chrootdir)
		free(j->chrootdir);
bad_chrootdir:
	if (j->hostname)
		free(j->hostname);
bad_hostname:
	if (j->suppl_gid_list)
		free(j->suppl_gid_list);
bad_gid_list:
	if (j->user)
		free(j->user);
clear_pointers:
	j->user = NULL;
	j->suppl_gid_list = NULL;
	j->chrootdir = NULL;
	j->hostname = NULL;
	j->alt_syscall_table = NULL;
	j->cgroup_count = 0;
out:
	return ret;
}

/*
 * mount_one: Applies mounts from @m for @j, recursing as needed.
 * @j Minijail these mounts are for
 * @m Head of list of mounts
 *
 * Returns 0 for success.
 */
static int mount_one(const struct minijail *j, struct mountpoint *m)
{
	int ret;
	char *dest;
	int remount_ro = 0;

	/* |dest| has a leading "/". */
	if (asprintf(&dest, "%s%s", j->chrootdir, m->dest) < 0)
		return -ENOMEM;

	if (setup_mount_destination(m->src, dest, j->uid, j->gid))
		pdie("creating mount target '%s' failed", dest);

	/*
	 * R/O bind mounts have to be remounted since 'bind' and 'ro'
	 * can't both be specified in the original bind mount.
	 * Remount R/O after the initial mount.
	 */
	if ((m->flags & MS_BIND) && (m->flags & MS_RDONLY)) {
		remount_ro = 1;
		m->flags &= ~MS_RDONLY;
	}

	ret = mount(m->src, dest, m->type, m->flags, m->data);
	if (ret)
		pdie("mount: %s -> %s", m->src, dest);

	if (remount_ro) {
		m->flags |= MS_RDONLY;
		ret = mount(m->src, dest, NULL,
			    m->flags | MS_REMOUNT, m->data);
		if (ret)
			pdie("bind ro: %s -> %s", m->src, dest);
	}

	free(dest);
	if (m->next)
		return mount_one(j, m->next);
	return ret;
}

static int enter_chroot(const struct minijail *j)
{
	int ret;

	if (j->mounts_head && (ret = mount_one(j, j->mounts_head)))
		return ret;

	if (chroot(j->chrootdir))
		return -errno;

	if (chdir("/"))
		return -errno;

	return 0;
}

static int enter_pivot_root(const struct minijail *j)
{
	int ret, oldroot, newroot;

	if (j->mounts_head && (ret = mount_one(j, j->mounts_head)))
		return ret;

	/*
	 * Keep the fd for both old and new root.
	 * It will be used in fchdir(2) later.
	 */
	oldroot = open("/", O_DIRECTORY | O_RDONLY | O_CLOEXEC);
	if (oldroot < 0)
		pdie("failed to open / for fchdir");
	newroot = open(j->chrootdir, O_DIRECTORY | O_RDONLY | O_CLOEXEC);
	if (newroot < 0)
		pdie("failed to open %s for fchdir", j->chrootdir);

	/*
	 * To ensure j->chrootdir is the root of a filesystem,
	 * do a self bind mount.
	 */
	if (mount(j->chrootdir, j->chrootdir, "bind", MS_BIND | MS_REC, ""))
		pdie("failed to bind mount '%s'", j->chrootdir);
	if (chdir(j->chrootdir))
		return -errno;
	if (syscall(SYS_pivot_root, ".", "."))
		pdie("pivot_root");

	/*
	 * Now the old root is mounted on top of the new root. Use fchdir(2) to
	 * change to the old root and unmount it.
	 */
	if (fchdir(oldroot))
		pdie("failed to fchdir to old /");

	/*
	 * If j->flags.skip_remount_private was enabled for minijail_enter(),
	 * there could be a shared mount point under |oldroot|. In that case,
	 * mounts under this shared mount point will be unmounted below, and
	 * this unmounting will propagate to the original mount namespace
	 * (because the mount point is shared). To prevent this unexpected
	 * unmounting, remove these mounts from their peer groups by recursively
	 * remounting them as MS_PRIVATE.
	 */
	if (mount(NULL, ".", NULL, MS_REC | MS_PRIVATE, NULL))
		pdie("failed to mount(/, private) before umount(/)");
	/* The old root might be busy, so use lazy unmount. */
	if (umount2(".", MNT_DETACH))
		pdie("umount(/)");
	/* Change back to the new root. */
	if (fchdir(newroot))
		return -errno;
	if (close(oldroot))
		return -errno;
	if (close(newroot))
		return -errno;
	if (chroot("/"))
		return -errno;
	/* Set correct CWD for getcwd(3). */
	if (chdir("/"))
		return -errno;

	return 0;
}

static int mount_tmp(const struct minijail *j)
{
	const char fmt[] = "size=%zu,mode=1777";
	/* Count for the user storing ULLONG_MAX literally + extra space. */
	char data[sizeof(fmt) + sizeof("18446744073709551615ULL")];
	int ret;

	ret = snprintf(data, sizeof(data), fmt, j->tmpfs_size);

	if (ret <= 0)
		pdie("tmpfs size spec error");
	else if ((size_t)ret >= sizeof(data))
		pdie("tmpfs size spec too large");
	return mount("none", "/tmp", "tmpfs", MS_NODEV | MS_NOEXEC | MS_NOSUID,
		     data);
}

static int remount_proc_readonly(const struct minijail *j)
{
	const char *kProcPath = "/proc";
	const unsigned int kSafeFlags = MS_NODEV | MS_NOEXEC | MS_NOSUID;
	/*
	 * Right now, we're holding a reference to our parent's old mount of
	 * /proc in our namespace, which means using MS_REMOUNT here would
	 * mutate our parent's mount as well, even though we're in a VFS
	 * namespace (!). Instead, remove their mount from our namespace lazily
	 * (MNT_DETACH) and make our own.
	 */
	if (umount2(kProcPath, MNT_DETACH)) {
		/*
		 * If we are in a new user namespace, umount(2) will fail.
		 * See http://man7.org/linux/man-pages/man7/user_namespaces.7.html
		 */
		if (j->flags.userns) {
			info("umount(/proc, MNT_DETACH) failed, "
			     "this is expected when using user namespaces");
		} else {
			return -errno;
		}
	}
	if (mount("proc", kProcPath, "proc", kSafeFlags | MS_RDONLY, ""))
		return -errno;
	return 0;
}

static void kill_child_and_die(const struct minijail *j, const char *msg)
{
	kill(j->initpid, SIGKILL);
	die("%s", msg);
}

static void write_pid_file_or_die(const struct minijail *j)
{
	if (write_pid_to_path(j->initpid, j->pid_file_path))
		kill_child_and_die(j, "failed to write pid file");
}

static void add_to_cgroups_or_die(const struct minijail *j)
{
	size_t i;

	for (i = 0; i < j->cgroup_count; ++i) {
		if (write_pid_to_path(j->initpid, j->cgroups[i]))
			kill_child_and_die(j, "failed to add to cgroups");
	}
}

static void set_rlimits_or_die(const struct minijail *j)
{
	size_t i;

	for (i = 0; i < j->rlimit_count; ++i) {
		struct rlimit limit;
		limit.rlim_cur = j->rlimits[i].cur;
		limit.rlim_max = j->rlimits[i].max;
		if (prlimit(j->initpid, j->rlimits[i].type, &limit, NULL))
			kill_child_and_die(j, "failed to set rlimit");
	}
}

static void write_ugid_maps_or_die(const struct minijail *j)
{
	if (j->uidmap && write_proc_file(j->initpid, j->uidmap, "uid_map") != 0)
		kill_child_and_die(j, "failed to write uid_map");
	if (j->gidmap && j->flags.disable_setgroups) {
		/* Older kernels might not have the /proc/<pid>/setgroups files. */
		int ret = write_proc_file(j->initpid, "deny", "setgroups");
		if (ret != 0) {
			if (ret == -ENOENT) {
				/* See http://man7.org/linux/man-pages/man7/user_namespaces.7.html. */
				warn("could not disable setgroups(2)");
			} else
				kill_child_and_die(j, "failed to disable setgroups(2)");
		}
	}
	if (j->gidmap && write_proc_file(j->initpid, j->gidmap, "gid_map") != 0)
		kill_child_and_die(j, "failed to write gid_map");
}

static void enter_user_namespace(const struct minijail *j)
{
	if (j->uidmap && setresuid(0, 0, 0))
		pdie("user_namespaces: setresuid(0, 0, 0) failed");
	if (j->gidmap && setresgid(0, 0, 0))
		pdie("user_namespaces: setresgid(0, 0, 0) failed");
}

static void parent_setup_complete(int *pipe_fds)
{
	close(pipe_fds[0]);
	close(pipe_fds[1]);
}

/*
 * wait_for_parent_setup: Called by the child process to wait for any
 * further parent-side setup to complete before continuing.
 */
static void wait_for_parent_setup(int *pipe_fds)
{
	char buf;

	close(pipe_fds[1]);

	/* Wait for parent to complete setup and close the pipe. */
	if (read(pipe_fds[0], &buf, 1) != 0)
		die("failed to sync with parent");
	close(pipe_fds[0]);
}

static void drop_ugid(const struct minijail *j)
{
	if (j->flags.inherit_suppl_gids + j->flags.keep_suppl_gids +
	    j->flags.set_suppl_gids > 1) {
		die("can only do one of inherit, keep, or set supplementary "
		    "groups");
	}

	if (j->flags.inherit_suppl_gids) {
		if (initgroups(j->user, j->usergid))
			pdie("initgroups(%s, %d) failed", j->user, j->usergid);
	} else if (j->flags.set_suppl_gids) {
		if (setgroups(j->suppl_gid_count, j->suppl_gid_list))
			pdie("setgroups(suppl_gids) failed");
	} else if (!j->flags.keep_suppl_gids) {
		/*
		 * Only attempt to clear supplementary groups if we are changing
		 * users or groups.
		 */
		if ((j->flags.uid || j->flags.gid) && setgroups(0, NULL))
			pdie("setgroups(0, NULL) failed");
	}

	if (j->flags.gid && setresgid(j->gid, j->gid, j->gid))
		pdie("setresgid(%d, %d, %d) failed", j->gid, j->gid, j->gid);

	if (j->flags.uid && setresuid(j->uid, j->uid, j->uid))
		pdie("setresuid(%d, %d, %d) failed", j->uid, j->uid, j->uid);
}

static void drop_capbset(uint64_t keep_mask, unsigned int last_valid_cap)
{
	const uint64_t one = 1;
	unsigned int i;
	for (i = 0; i < sizeof(keep_mask) * 8 && i <= last_valid_cap; ++i) {
		if (keep_mask & (one << i))
			continue;
		if (prctl(PR_CAPBSET_DROP, i))
			pdie("could not drop capability from bounding set");
	}
}

static void drop_caps(const struct minijail *j, unsigned int last_valid_cap)
{
	if (!j->flags.use_caps)
		return;

	cap_t caps = cap_get_proc();
	cap_value_t flag[1];
	const size_t ncaps = sizeof(j->caps) * 8;
	const uint64_t one = 1;
	unsigned int i;
	if (!caps)
		die("can't get process caps");
	if (cap_clear(caps))
		die("can't clear caps");

	for (i = 0; i < ncaps && i <= last_valid_cap; ++i) {
		/* Keep CAP_SETPCAP for dropping bounding set bits. */
		if (i != CAP_SETPCAP && !(j->caps & (one << i)))
			continue;
		flag[0] = i;
		if (cap_set_flag(caps, CAP_EFFECTIVE, 1, flag, CAP_SET))
			die("can't add effective cap");
		if (cap_set_flag(caps, CAP_PERMITTED, 1, flag, CAP_SET))
			die("can't add permitted cap");
		if (cap_set_flag(caps, CAP_INHERITABLE, 1, flag, CAP_SET))
			die("can't add inheritable cap");
	}
	if (cap_set_proc(caps))
		die("can't apply initial cleaned capset");

	/*
	 * Instead of dropping bounding set first, do it here in case
	 * the caller had a more permissive bounding set which could
	 * have been used above to raise a capability that wasn't already
	 * present. This requires CAP_SETPCAP, so we raised/kept it above.
	 */
	drop_capbset(j->caps, last_valid_cap);

	/* If CAP_SETPCAP wasn't specifically requested, now we remove it. */
	if ((j->caps & (one << CAP_SETPCAP)) == 0) {
		flag[0] = CAP_SETPCAP;
		if (cap_set_flag(caps, CAP_EFFECTIVE, 1, flag, CAP_CLEAR))
			die("can't clear effective cap");
		if (cap_set_flag(caps, CAP_PERMITTED, 1, flag, CAP_CLEAR))
			die("can't clear permitted cap");
		if (cap_set_flag(caps, CAP_INHERITABLE, 1, flag, CAP_CLEAR))
			die("can't clear inheritable cap");
	}

	if (cap_set_proc(caps))
		die("can't apply final cleaned capset");

	/*
	 * If ambient capabilities are supported, clear all capabilities first,
	 * then raise the requested ones.
	 */
	if (j->flags.set_ambient_caps) {
		if (!cap_ambient_supported()) {
			pdie("ambient capabilities not supported");
		}
		if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0) !=
		    0) {
			pdie("can't clear ambient capabilities");
		}

		for (i = 0; i < ncaps && i <= last_valid_cap; ++i) {
			if (!(j->caps & (one << i)))
				continue;

			if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, i, 0,
				  0) != 0) {
				pdie("prctl(PR_CAP_AMBIENT, "
				     "PR_CAP_AMBIENT_RAISE, %u) failed",
				     i);
			}
		}
	}

	cap_free(caps);
}

static void set_seccomp_filter(const struct minijail *j)
{
	/*
	 * Set no_new_privs. See </kernel/seccomp.c> and </kernel/sys.c>
	 * in the kernel source tree for an explanation of the parameters.
	 */
	if (j->flags.no_new_privs) {
		if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0))
			pdie("prctl(PR_SET_NO_NEW_PRIVS)");
	}

	/*
	 * Code running with ASan
	 * (https://github.com/google/sanitizers/wiki/AddressSanitizer)
	 * will make system calls not included in the syscall filter policy,
	 * which will likely crash the program. Skip setting seccomp filter in
	 * that case.
	 * 'running_with_asan()' has no inputs and is completely defined at
	 * build time, so this cannot be used by an attacker to skip setting
	 * seccomp filter.
	 */
	if (j->flags.seccomp_filter && running_with_asan()) {
		warn("running with ASan, not setting seccomp filter");
		return;
	}

	if (j->flags.seccomp_filter) {
		if (j->flags.seccomp_filter_logging) {
			/*
			 * If logging seccomp filter failures,
			 * install the SIGSYS handler first.
			 */
			if (install_sigsys_handler())
				pdie("failed to install SIGSYS handler");
			warn("logging seccomp filter failures");
		} else if (j->flags.seccomp_filter_tsync) {
			/*
			 * If setting thread sync,
			 * reset the SIGSYS signal handler so that
			 * the entire thread group is killed.
			 */
			if (signal(SIGSYS, SIG_DFL) == SIG_ERR)
				pdie("failed to reset SIGSYS disposition");
			info("reset SIGSYS disposition");
		}
	}

	/*
	 * Install the syscall filter.
	 */
	if (j->flags.seccomp_filter) {
		if (j->flags.seccomp_filter_tsync) {
			if (sys_seccomp(SECCOMP_SET_MODE_FILTER,
					SECCOMP_FILTER_FLAG_TSYNC,
					j->filter_prog)) {
				pdie("seccomp(tsync) failed");
			}
		} else {
			if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER,
				  j->filter_prog)) {
				pdie("prctl(seccomp_filter) failed");
			}
		}
	}
}

static pid_t forward_pid = -1;

static void forward_signal(__attribute__((unused)) int nr,
			   __attribute__((unused)) siginfo_t *siginfo,
			   __attribute__((unused)) void *void_context)
{
	if (forward_pid != -1) {
		kill(forward_pid, nr);
	}
}

static void install_signal_handlers(void)
{
	struct sigaction act;

	memset(&act, 0, sizeof(act));
	act.sa_sigaction = &forward_signal;
	act.sa_flags = SA_SIGINFO | SA_RESTART;

	/* Handle all signals, except SIGCHLD. */
	for (int nr = 1; nr < NSIG; nr++) {
		/*
		 * We don't care if we get EINVAL: that just means that we
		 * can't handle this signal, so let's skip it and continue.
		 */
		sigaction(nr, &act, NULL);
	}
	/* Reset SIGCHLD's handler. */
	signal(SIGCHLD, SIG_DFL);

	/* Handle real-time signals. */
	for (int nr = SIGRTMIN; nr <= SIGRTMAX; nr++) {
		sigaction(nr, &act, NULL);
	}
}

void API minijail_enter(const struct minijail *j)
{
	/*
	 * If we're dropping caps, get the last valid cap from /proc now,
	 * since /proc can be unmounted before drop_caps() is called.
	 */
	unsigned int last_valid_cap = 0;
	if (j->flags.capbset_drop || j->flags.use_caps)
		last_valid_cap = get_last_valid_cap();

	if (j->flags.pids)
		die("tried to enter a pid-namespaced jail;"
		    " try minijail_run()?");

	if (j->flags.inherit_suppl_gids && !j->user)
		die("cannot inherit supplementary groups without setting a "
		    "username");

	/*
	 * We can't recover from failures if we've dropped privileges partially,
	 * so we don't even try. If any of our operations fail, we abort() the
	 * entire process.
	 */
	if (j->flags.enter_vfs && setns(j->mountns_fd, CLONE_NEWNS))
		pdie("setns(CLONE_NEWNS) failed");

	if (j->flags.vfs) {
		if (unshare(CLONE_NEWNS))
			pdie("unshare(CLONE_NEWNS) failed");
		/*
		 * Unless asked not to, remount all filesystems as private.
		 * If they are shared, new bind mounts will creep out of our
		 * namespace.
		 * https://www.kernel.org/doc/Documentation/filesystems/sharedsubtree.txt
		 */
		if (!j->flags.skip_remount_private) {
			if (mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL))
				pdie("mount(NULL, /, NULL, MS_REC | MS_PRIVATE,"
				     " NULL) failed");
		}
	}

	if (j->flags.ipc && unshare(CLONE_NEWIPC)) {
		pdie("unshare(CLONE_NEWIPC) failed");
	}

	if (j->flags.uts) {
		if (unshare(CLONE_NEWUTS))
			pdie("unshare(CLONE_NEWUTS) failed");

		if (j->hostname && sethostname(j->hostname, strlen(j->hostname)))
			pdie("sethostname(%s) failed", j->hostname);
	}

	if (j->flags.enter_net) {
		if (setns(j->netns_fd, CLONE_NEWNET))
			pdie("setns(CLONE_NEWNET) failed");
	} else if (j->flags.net) {
		if (unshare(CLONE_NEWNET))
			pdie("unshare(CLONE_NEWNET) failed");
		config_net_loopback();
	}

	if (j->flags.ns_cgroups && unshare(CLONE_NEWCGROUP))
		pdie("unshare(CLONE_NEWCGROUP) failed");

	if (j->flags.new_session_keyring) {
		if (syscall(SYS_keyctl, KEYCTL_JOIN_SESSION_KEYRING, NULL) < 0)
			pdie("keyctl(KEYCTL_JOIN_SESSION_KEYRING) failed");
	}

	if (j->flags.chroot && enter_chroot(j))
		pdie("chroot");

	if (j->flags.pivot_root && enter_pivot_root(j))
		pdie("pivot_root");

	if (j->flags.mount_tmp && mount_tmp(j))
		pdie("mount_tmp");

	if (j->flags.remount_proc_ro && remount_proc_readonly(j))
		pdie("remount");

	/*
	 * If we're only dropping capabilities from the bounding set, but not
	 * from the thread's (permitted|inheritable|effective) sets, do it now.
	 */
	if (j->flags.capbset_drop) {
		drop_capbset(j->cap_bset, last_valid_cap);
	}

	if (j->flags.use_caps) {
		/*
		 * POSIX capabilities are a bit tricky. If we drop our
		 * capability to change uids, our attempt to use setuid()
		 * below will fail. Hang on to root caps across setuid(), then
		 * lock securebits.
		 */
		if (prctl(PR_SET_KEEPCAPS, 1))
			pdie("prctl(PR_SET_KEEPCAPS) failed");

		if (lock_securebits(j->securebits_skip_mask) < 0) {
			pdie("locking securebits failed");
		}
	}

	if (j->flags.no_new_privs) {
		/*
		 * If we're setting no_new_privs, we can drop privileges
		 * before setting seccomp filter. This way filter policies
		 * don't need to allow privilege-dropping syscalls.
		 */
		drop_ugid(j);
		drop_caps(j, last_valid_cap);
		set_seccomp_filter(j);
	} else {
		/*
		 * If we're not setting no_new_privs,
		 * we need to set seccomp filter *before* dropping privileges.
		 * WARNING: this means that filter policies *must* allow
		 * setgroups()/setresgid()/setresuid() for dropping root and
		 * capget()/capset()/prctl() for dropping caps.
		 */
		set_seccomp_filter(j);
		drop_ugid(j);
		drop_caps(j, last_valid_cap);
	}

	/*
	 * Select the specified alternate syscall table.  The table must not
	 * block prctl(2) if we're using seccomp as well.
	 */
	if (j->flags.alt_syscall) {
		if (prctl(PR_ALT_SYSCALL, 1, j->alt_syscall_table))
			pdie("prctl(PR_ALT_SYSCALL) failed");
	}

	/*
	 * seccomp has to come last since it cuts off all the other
	 * privilege-dropping syscalls :)
	 */
	if (j->flags.seccomp && prctl(PR_SET_SECCOMP, 1)) {
		if ((errno == EINVAL) && seccomp_can_softfail()) {
			warn("seccomp not supported");
			return;
		}
		pdie("prctl(PR_SET_SECCOMP) failed");
	}
}

/* TODO(wad): will visibility affect this variable? */
static int init_exitstatus = 0;

void init_term(int __attribute__ ((unused)) sig)
{
	_exit(init_exitstatus);
}

void init(pid_t rootpid)
{
	pid_t pid;
	int status;
	/* So that we exit with the right status. */
	signal(SIGTERM, init_term);
	/* TODO(wad): self jail with seccomp filters here. */
	while ((pid = wait(&status)) > 0) {
		/*
		 * This loop will only end when either there are no processes
		 * left inside our pid namespace or we get a signal.
		 */
		if (pid == rootpid)
			init_exitstatus = status;
	}
	if (!WIFEXITED(init_exitstatus))
		_exit(MINIJAIL_ERR_INIT);
	_exit(WEXITSTATUS(init_exitstatus));
}

int API minijail_from_fd(int fd, struct minijail *j)
{
	size_t sz = 0;
	size_t bytes = read(fd, &sz, sizeof(sz));
	char *buf;
	int r;
	if (sizeof(sz) != bytes)
		return -EINVAL;
	if (sz > USHRT_MAX)	/* arbitrary sanity check */
		return -E2BIG;
	buf = malloc(sz);
	if (!buf)
		return -ENOMEM;
	bytes = read(fd, buf, sz);
	if (bytes != sz) {
		free(buf);
		return -EINVAL;
	}
	r = minijail_unmarshal(j, buf, sz);
	free(buf);
	return r;
}

int API minijail_to_fd(struct minijail *j, int fd)
{
	char *buf;
	size_t sz = minijail_size(j);
	ssize_t written;
	int r;

	if (!sz)
		return -EINVAL;
	buf = malloc(sz);
	r = minijail_marshal(j, buf, sz);
	if (r) {
		free(buf);
		return r;
	}
	/* Sends [size][minijail]. */
	written = write(fd, &sz, sizeof(sz));
	if (written != sizeof(sz)) {
		free(buf);
		return -EFAULT;
	}
	written = write(fd, buf, sz);
	if (written < 0 || (size_t) written != sz) {
		free(buf);
		return -EFAULT;
	}
	free(buf);
	return 0;
}

int setup_preload(void)
{
#if defined(__ANDROID__)
	/* Don't use LDPRELOAD on Android. */
	return 0;
#else
	char *oldenv = getenv(kLdPreloadEnvVar) ? : "";
	char *newenv = malloc(strlen(oldenv) + 2 + strlen(PRELOADPATH));
	if (!newenv)
		return -ENOMEM;

	/* Only insert a separating space if we have something to separate... */
	sprintf(newenv, "%s%s%s", oldenv, strlen(oldenv) ? " " : "",
		PRELOADPATH);

	/* setenv() makes a copy of the string we give it. */
	setenv(kLdPreloadEnvVar, newenv, 1);
	free(newenv);
	return 0;
#endif
}

static int setup_pipe(int fds[2])
{
	int r = pipe(fds);
	char fd_buf[11];
	if (r)
		return r;
	r = snprintf(fd_buf, sizeof(fd_buf), "%d", fds[0]);
	if (r <= 0)
		return -EINVAL;
	setenv(kFdEnvVar, fd_buf, 1);
	return 0;
}

static int close_open_fds(int *inheritable_fds, size_t size)
{
	const char *kFdPath = "/proc/self/fd";

	DIR *d = opendir(kFdPath);
	struct dirent *dir_entry;

	if (d == NULL)
		return -1;
	int dir_fd = dirfd(d);
	while ((dir_entry = readdir(d)) != NULL) {
		size_t i;
		char *end;
		bool should_close = true;
		const int fd = strtol(dir_entry->d_name, &end, 10);

		if ((*end) != '\0') {
			continue;
		}
		/*
		 * We might have set up some pipes that we want to share with
		 * the parent process, and should not be closed.
		 */
		for (i = 0; i < size; ++i) {
			if (fd == inheritable_fds[i]) {
				should_close = false;
				break;
			}
		}
		/* Also avoid closing the directory fd. */
		if (should_close && fd != dir_fd)
			close(fd);
	}
	closedir(d);
	return 0;
}

int minijail_run_internal(struct minijail *j, const char *filename,
			  char *const argv[], pid_t *pchild_pid,
			  int *pstdin_fd, int *pstdout_fd, int *pstderr_fd,
			  int use_preload);

int API minijail_run(struct minijail *j, const char *filename,
		     char *const argv[])
{
	return minijail_run_internal(j, filename, argv, NULL, NULL, NULL, NULL,
				     true);
}

int API minijail_run_pid(struct minijail *j, const char *filename,
			 char *const argv[], pid_t *pchild_pid)
{
	return minijail_run_internal(j, filename, argv, pchild_pid,
				     NULL, NULL, NULL, true);
}

int API minijail_run_pipe(struct minijail *j, const char *filename,
			  char *const argv[], int *pstdin_fd)
{
	return minijail_run_internal(j, filename, argv, NULL, pstdin_fd,
				     NULL, NULL, true);
}

int API minijail_run_pid_pipes(struct minijail *j, const char *filename,
			       char *const argv[], pid_t *pchild_pid,
			       int *pstdin_fd, int *pstdout_fd, int *pstderr_fd)
{
	return minijail_run_internal(j, filename, argv, pchild_pid,
				     pstdin_fd, pstdout_fd, pstderr_fd, true);
}

int API minijail_run_no_preload(struct minijail *j, const char *filename,
				char *const argv[])
{
	return minijail_run_internal(j, filename, argv, NULL, NULL, NULL, NULL,
				     false);
}

int API minijail_run_pid_pipes_no_preload(struct minijail *j,
					  const char *filename,
					  char *const argv[],
					  pid_t *pchild_pid,
					  int *pstdin_fd, int *pstdout_fd,
					  int *pstderr_fd)
{
	return minijail_run_internal(j, filename, argv, pchild_pid,
				     pstdin_fd, pstdout_fd, pstderr_fd, false);
}

int minijail_run_internal(struct minijail *j, const char *filename,
			  char *const argv[], pid_t *pchild_pid,
			  int *pstdin_fd, int *pstdout_fd, int *pstderr_fd,
			  int use_preload)
{
	char *oldenv, *oldenv_copy = NULL;
	pid_t child_pid;
	int pipe_fds[2];
	int stdin_fds[2];
	int stdout_fds[2];
	int stderr_fds[2];
	int child_sync_pipe_fds[2];
	int sync_child = 0;
	int ret;
	/* We need to remember this across the minijail_preexec() call. */
	int pid_namespace = j->flags.pids;
	int do_init = j->flags.do_init;

	if (use_preload) {
		oldenv = getenv(kLdPreloadEnvVar);
		if (oldenv) {
			oldenv_copy = strdup(oldenv);
			if (!oldenv_copy)
				return -ENOMEM;
		}

		if (setup_preload())
			return -EFAULT;
	}

	if (!use_preload) {
		if (j->flags.use_caps && j->caps != 0 &&
		    !j->flags.set_ambient_caps) {
			die("non-empty, non-ambient capabilities are not "
			    "supported without LD_PRELOAD");
		}
	}

	/*
	 * Make the process group ID of this process equal to its PID.
	 * In the non-interactive case (e.g. when the parent process is started
	 * from init) this ensures the parent process and the jailed process
	 * can be killed together.
	 * When the parent process is started from the console this ensures
	 * the call to setsid(2) in the jailed process succeeds.
	 *
	 * Don't fail on EPERM, since setpgid(0, 0) can only EPERM when
	 * the process is already a process group leader.
	 */
	if (setpgid(0 /* use calling PID */, 0 /* make PGID = PID */)) {
		if (errno != EPERM) {
			pdie("setpgid(0, 0) failed");
		}
	}

	if (use_preload) {
		/*
		 * Before we fork(2) and execve(2) the child process, we need
		 * to open a pipe(2) to send the minijail configuration over.
		 */
		if (setup_pipe(pipe_fds))
			return -EFAULT;
	}

	/*
	 * If we want to write to the child process' standard input,
	 * create the pipe(2) now.
	 */
	if (pstdin_fd) {
		if (pipe(stdin_fds))
			return -EFAULT;
	}

	/*
	 * If we want to read from the child process' standard output,
	 * create the pipe(2) now.
	 */
	if (pstdout_fd) {
		if (pipe(stdout_fds))
			return -EFAULT;
	}

	/*
	 * If we want to read from the child process' standard error,
	 * create the pipe(2) now.
	 */
	if (pstderr_fd) {
		if (pipe(stderr_fds))
			return -EFAULT;
	}

	/*
	 * If we want to set up a new uid/gid map in the user namespace,
	 * or if we need to add the child process to cgroups, create the pipe(2)
	 * to sync between parent and child.
	 */
	if (j->flags.userns || j->flags.cgroups) {
		sync_child = 1;
		if (pipe(child_sync_pipe_fds))
			return -EFAULT;
	}

	/*
	 * Use sys_clone() if and only if we're creating a pid namespace.
	 *
	 * tl;dr: WARNING: do not mix pid namespaces and multithreading.
	 *
	 * In multithreaded programs, there are a bunch of locks inside libc,
	 * some of which may be held by other threads at the time that we call
	 * minijail_run_pid(). If we call fork(), glibc does its level best to
	 * ensure that we hold all of these locks before it calls clone()
	 * internally and drop them after clone() returns, but when we call
	 * sys_clone(2) directly, all that gets bypassed and we end up with a
	 * child address space where some of libc's important locks are held by
	 * other threads (which did not get cloned, and hence will never release
	 * those locks). This is okay so long as we call exec() immediately
	 * after, but a bunch of seemingly-innocent libc functions like setenv()
	 * take locks.
	 *
	 * Hence, only call sys_clone() if we need to, in order to get at pid
	 * namespacing. If we follow this path, the child's address space might
	 * have broken locks; you may only call functions that do not acquire
	 * any locks.
	 *
	 * Unfortunately, fork() acquires every lock it can get its hands on, as
	 * previously detailed, so this function is highly likely to deadlock
	 * later on (see "deadlock here") if we're multithreaded.
	 *
	 * We might hack around this by having the clone()d child (init of the
	 * pid namespace) return directly, rather than leaving the clone()d
	 * process hanging around to be init for the new namespace (and having
	 * its fork()ed child return in turn), but that process would be
	 * crippled with its libc locks potentially broken. We might try
	 * fork()ing in the parent before we clone() to ensure that we own all
	 * the locks, but then we have to have the forked child hanging around
	 * consuming resources (and possibly having file descriptors / shared
	 * memory regions / etc attached). We'd need to keep the child around to
	 * avoid having its children get reparented to init.
	 *
	 * TODO(ellyjones): figure out if the "forked child hanging around"
	 * problem is fixable or not. It would be nice if we worked in this
	 * case.
	 */
	if (pid_namespace) {
		int clone_flags = CLONE_NEWPID | SIGCHLD;
		if (j->flags.userns)
			clone_flags |= CLONE_NEWUSER;
		child_pid = syscall(SYS_clone, clone_flags, NULL);
	} else {
		child_pid = fork();
	}

	if (child_pid < 0) {
		if (use_preload) {
			free(oldenv_copy);
		}
		die("failed to fork child");
	}

	if (child_pid) {
		if (use_preload) {
			/* Restore parent's LD_PRELOAD. */
			if (oldenv_copy) {
				setenv(kLdPreloadEnvVar, oldenv_copy, 1);
				free(oldenv_copy);
			} else {
				unsetenv(kLdPreloadEnvVar);
			}
			unsetenv(kFdEnvVar);
		}

		j->initpid = child_pid;

		if (j->flags.forward_signals) {
			forward_pid = child_pid;
			install_signal_handlers();
		}

		if (j->flags.pid_file)
			write_pid_file_or_die(j);

		if (j->flags.cgroups)
			add_to_cgroups_or_die(j);

		if (j->rlimit_count)
			set_rlimits_or_die(j);

		if (j->flags.userns)
			write_ugid_maps_or_die(j);

		if (sync_child)
			parent_setup_complete(child_sync_pipe_fds);

		if (use_preload) {
			/* Send marshalled minijail. */
			close(pipe_fds[0]);	/* read endpoint */
			ret = minijail_to_fd(j, pipe_fds[1]);
			close(pipe_fds[1]);	/* write endpoint */
			if (ret) {
				kill(j->initpid, SIGKILL);
				die("failed to send marshalled minijail");
			}
		}

		if (pchild_pid)
			*pchild_pid = child_pid;

		/*
		 * If we want to write to the child process' standard input,
		 * set up the write end of the pipe.
		 */
		if (pstdin_fd)
			*pstdin_fd = setup_pipe_end(stdin_fds,
						    1 /* write end */);

		/*
		 * If we want to read from the child process' standard output,
		 * set up the read end of the pipe.
		 */
		if (pstdout_fd)
			*pstdout_fd = setup_pipe_end(stdout_fds,
						     0 /* read end */);

		/*
		 * If we want to read from the child process' standard error,
		 * set up the read end of the pipe.
		 */
		if (pstderr_fd)
			*pstderr_fd = setup_pipe_end(stderr_fds,
						     0 /* read end */);

		return 0;
	}
	/* Child process. */
	free(oldenv_copy);

	if (j->flags.reset_signal_mask) {
		sigset_t signal_mask;
		if (sigemptyset(&signal_mask) != 0)
			pdie("sigemptyset failed");
		if (sigprocmask(SIG_SETMASK, &signal_mask, NULL) != 0)
			pdie("sigprocmask failed");
	}

	if (j->flags.close_open_fds) {
		const size_t kMaxInheritableFdsSize = 10;
		int inheritable_fds[kMaxInheritableFdsSize];
		size_t size = 0;
		if (use_preload) {
			inheritable_fds[size++] = pipe_fds[0];
			inheritable_fds[size++] = pipe_fds[1];
		}
		if (sync_child) {
			inheritable_fds[size++] = child_sync_pipe_fds[0];
			inheritable_fds[size++] = child_sync_pipe_fds[1];
		}
		if (pstdin_fd) {
			inheritable_fds[size++] = stdin_fds[0];
			inheritable_fds[size++] = stdin_fds[1];
		}
		if (pstdout_fd) {
			inheritable_fds[size++] = stdout_fds[0];
			inheritable_fds[size++] = stdout_fds[1];
		}
		if (pstderr_fd) {
			inheritable_fds[size++] = stderr_fds[0];
			inheritable_fds[size++] = stderr_fds[1];
		}

		if (close_open_fds(inheritable_fds, size) < 0)
			die("failed to close open file descriptors");
	}

	if (sync_child)
		wait_for_parent_setup(child_sync_pipe_fds);

	if (j->flags.userns)
		enter_user_namespace(j);

	/*
	 * If we want to write to the jailed process' standard input,
	 * set up the read end of the pipe.
	 */
	if (pstdin_fd) {
		if (setup_and_dupe_pipe_end(stdin_fds, 0 /* read end */,
					    STDIN_FILENO) < 0)
			die("failed to set up stdin pipe");
	}

	/*
	 * If we want to read from the jailed process' standard output,
	 * set up the write end of the pipe.
	 */
	if (pstdout_fd) {
		if (setup_and_dupe_pipe_end(stdout_fds, 1 /* write end */,
					    STDOUT_FILENO) < 0)
			die("failed to set up stdout pipe");
	}

	/*
	 * If we want to read from the jailed process' standard error,
	 * set up the write end of the pipe.
	 */
	if (pstderr_fd) {
		if (setup_and_dupe_pipe_end(stderr_fds, 1 /* write end */,
					    STDERR_FILENO) < 0)
			die("failed to set up stderr pipe");
	}

	/*
	 * If any of stdin, stdout, or stderr are TTYs, create a new session.
	 * This prevents the jailed process from using the TIOCSTI ioctl
	 * to push characters into the parent process terminal's input buffer,
	 * therefore escaping the jail.
	 */
	if (isatty(STDIN_FILENO) || isatty(STDOUT_FILENO) ||
	    isatty(STDERR_FILENO)) {
		if (setsid() < 0) {
			pdie("setsid() failed");
		}
	}

	/* If running an init program, let it decide when/how to mount /proc. */
	if (pid_namespace && !do_init)
		j->flags.remount_proc_ro = 0;

	if (use_preload) {
		/* Strip out flags that cannot be inherited across execve(2). */
		minijail_preexec(j);
	} else {
		/*
		 * If not using LD_PRELOAD, do all jailing before execve(2).
		 * Note that PID namespaces can only be entered on fork(2),
		 * so that flag is still cleared.
		 */
		j->flags.pids = 0;
	}
	/* Jail this process, then execve(2) the target. */
	minijail_enter(j);

	if (pid_namespace && do_init) {
		/*
		 * pid namespace: this process will become init inside the new
		 * namespace. We don't want all programs we might exec to have
		 * to know how to be init. Normally (do_init == 1) we fork off
		 * a child to actually run the program. If |do_init == 0|, we
		 * let the program keep pid 1 and be init.
		 *
		 * If we're multithreaded, we'll probably deadlock here. See
		 * WARNING above.
		 */
		child_pid = fork();
		if (child_pid < 0) {
			_exit(child_pid);
		} else if (child_pid > 0) {
			/*
			 * Best effort. Don't bother checking the return value.
			 */
			prctl(PR_SET_NAME, "minijail-init");
			init(child_pid);	/* Never returns. */
		}
	}

	/*
	 * If we aren't pid-namespaced, or the jailed program asked to be init:
	 *   calling process
	 *   -> execve()-ing process
	 * If we are:
	 *   calling process
	 *   -> init()-ing process
	 *      -> execve()-ing process
	 */
	ret = execve(filename, argv, environ);
	if (ret == -1) {
		pwarn("execve(%s) failed", filename);
	}
	_exit(ret);
}

int API minijail_kill(struct minijail *j)
{
	int st;
	if (kill(j->initpid, SIGTERM))
		return -errno;
	if (waitpid(j->initpid, &st, 0) < 0)
		return -errno;
	return st;
}

int API minijail_wait(struct minijail *j)
{
	int st;
	if (waitpid(j->initpid, &st, 0) < 0)
		return -errno;

	if (!WIFEXITED(st)) {
		int error_status = st;
		if (WIFSIGNALED(st)) {
			int signum = WTERMSIG(st);
			warn("child process %d received signal %d",
			     j->initpid, signum);
			/*
			 * We return MINIJAIL_ERR_JAIL if the process received
			 * SIGSYS, which happens when a syscall is blocked by
			 * seccomp filters.
			 * If not, we do what bash(1) does:
			 * $? = 128 + signum
			 */
			if (signum == SIGSYS) {
				error_status = MINIJAIL_ERR_JAIL;
			} else {
				error_status = 128 + signum;
			}
		}
		return error_status;
	}

	int exit_status = WEXITSTATUS(st);
	if (exit_status != 0)
		info("child process %d exited with status %d",
		     j->initpid, exit_status);

	return exit_status;
}

void API minijail_destroy(struct minijail *j)
{
	size_t i;

	if (j->flags.seccomp_filter && j->filter_prog) {
		free(j->filter_prog->filter);
		free(j->filter_prog);
	}
	while (j->mounts_head) {
		struct mountpoint *m = j->mounts_head;
		j->mounts_head = j->mounts_head->next;
		free(m->data);
		free(m->type);
		free(m->dest);
		free(m->src);
		free(m);
	}
	j->mounts_tail = NULL;
	if (j->user)
		free(j->user);
	if (j->suppl_gid_list)
		free(j->suppl_gid_list);
	if (j->chrootdir)
		free(j->chrootdir);
	if (j->pid_file_path)
		free(j->pid_file_path);
	if (j->uidmap)
		free(j->uidmap);
	if (j->gidmap)
		free(j->gidmap);
	if (j->hostname)
		free(j->hostname);
	if (j->alt_syscall_table)
		free(j->alt_syscall_table);
	for (i = 0; i < j->cgroup_count; ++i)
		free(j->cgroups[i]);
	free(j);
}
