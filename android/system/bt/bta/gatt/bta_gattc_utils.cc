/******************************************************************************
 *
 *  Copyright (C) 2003-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  This file contains the GATT client utility function.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_gattc"

#include "bt_target.h"

#include <base/logging.h>
#include <string.h>

#include "bt_common.h"
#include "bta_gattc_int.h"
#include "bta_sys.h"
#include "l2c_api.h"
#include "utl.h"

/*****************************************************************************
 *  Constants
 ****************************************************************************/

static const uint8_t base_uuid[LEN_UUID_128] = {
    0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80,
    0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

/*******************************************************************************
 *
 * Function         bta_gatt_convert_uuid16_to_uuid128
 *
 * Description      Convert a 16 bits UUID to be an standard 128 bits one.
 *
 * Returns          true if two uuid match; false otherwise.
 *
 ******************************************************************************/
void bta_gatt_convert_uuid16_to_uuid128(uint8_t uuid_128[LEN_UUID_128],
                                        uint16_t uuid_16) {
  uint8_t* p = &uuid_128[LEN_UUID_128 - 4];

  memcpy(uuid_128, base_uuid, LEN_UUID_128);

  UINT16_TO_STREAM(p, uuid_16);
}
/*******************************************************************************
 *
 * Function         bta_gattc_uuid_compare
 *
 * Description      Compare two UUID to see if they are the same.
 *
 * Returns          true if two uuid match; false otherwise.
 *
 ******************************************************************************/
bool bta_gattc_uuid_compare(const tBT_UUID* p_src, const tBT_UUID* p_tar,
                            bool is_precise) {
  uint8_t su[LEN_UUID_128], tu[LEN_UUID_128];
  const uint8_t *ps, *pt;

  /* any of the UUID is unspecified */
  if (p_src == 0 || p_tar == 0) {
    if (is_precise)
      return false;
    else
      return true;
  }

  /* If both are 16-bit, we can do a simple compare */
  if (p_src->len == 2 && p_tar->len == 2) {
    return p_src->uu.uuid16 == p_tar->uu.uuid16;
  }

  /* One or both of the UUIDs is 128-bit */
  if (p_src->len == LEN_UUID_16) {
    /* convert a 16 bits UUID to 128 bits value */
    bta_gatt_convert_uuid16_to_uuid128(su, p_src->uu.uuid16);
    ps = su;
  } else
    ps = p_src->uu.uuid128;

  if (p_tar->len == LEN_UUID_16) {
    /* convert a 16 bits UUID to 128 bits value */
    bta_gatt_convert_uuid16_to_uuid128(tu, p_tar->uu.uuid16);
    pt = tu;
  } else
    pt = p_tar->uu.uuid128;

  return (memcmp(ps, pt, LEN_UUID_128) == 0);
}

/*******************************************************************************
 *
 * Function         bta_gattc_cl_get_regcb
 *
 * Description      get registration control block by client interface.
 *
 * Returns          pointer to the regcb
 *
 ******************************************************************************/
tBTA_GATTC_RCB* bta_gattc_cl_get_regcb(uint8_t client_if) {
  uint8_t i = 0;
  tBTA_GATTC_RCB* p_clrcb = &bta_gattc_cb.cl_rcb[0];

  for (i = 0; i < BTA_GATTC_CL_MAX; i++, p_clrcb++) {
    if (p_clrcb->in_use && p_clrcb->client_if == client_if) return p_clrcb;
  }
  return NULL;
}
/*******************************************************************************
 *
 * Function         bta_gattc_num_reg_app
 *
 * Description      find the number of registered application.
 *
 * Returns          pointer to the regcb
 *
 ******************************************************************************/
uint8_t bta_gattc_num_reg_app(void) {
  uint8_t i = 0, j = 0;

  for (i = 0; i < BTA_GATTC_CL_MAX; i++) {
    if (bta_gattc_cb.cl_rcb[i].in_use) j++;
  }
  return j;
}
/*******************************************************************************
 *
 * Function         bta_gattc_find_clcb_by_cif
 *
 * Description      get clcb by client interface and remote bd adddress
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_find_clcb_by_cif(uint8_t client_if,
                                            const RawAddress& remote_bda,
                                            tBTA_TRANSPORT transport) {
  tBTA_GATTC_CLCB* p_clcb = &bta_gattc_cb.clcb[0];
  uint8_t i;

  for (i = 0; i < BTA_GATTC_CLCB_MAX; i++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->p_rcb->client_if == client_if &&
        p_clcb->transport == transport && p_clcb->bda == remote_bda)
      return p_clcb;
  }
  return NULL;
}
/*******************************************************************************
 *
 * Function         bta_gattc_find_clcb_by_conn_id
 *
 * Description      get clcb by connection ID
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_find_clcb_by_conn_id(uint16_t conn_id) {
  tBTA_GATTC_CLCB* p_clcb = &bta_gattc_cb.clcb[0];
  uint8_t i;

  for (i = 0; i < BTA_GATTC_CLCB_MAX; i++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->bta_conn_id == conn_id) return p_clcb;
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         bta_gattc_clcb_alloc
 *
 * Description      allocate CLCB
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_clcb_alloc(tBTA_GATTC_IF client_if,
                                      const RawAddress& remote_bda,
                                      tBTA_TRANSPORT transport) {
  uint8_t i_clcb = 0;
  tBTA_GATTC_CLCB* p_clcb = NULL;

  for (i_clcb = 0; i_clcb < BTA_GATTC_CLCB_MAX; i_clcb++) {
    if (!bta_gattc_cb.clcb[i_clcb].in_use) {
#if (BTA_GATT_DEBUG == TRUE)
      APPL_TRACE_DEBUG("bta_gattc_clcb_alloc: found clcb[%d] available",
                       i_clcb);
#endif
      p_clcb = &bta_gattc_cb.clcb[i_clcb];
      p_clcb->in_use = true;
      p_clcb->status = BTA_GATT_OK;
      p_clcb->transport = transport;
      p_clcb->bda = remote_bda;

      p_clcb->p_rcb = bta_gattc_cl_get_regcb(client_if);

      p_clcb->p_srcb = bta_gattc_find_srcb(remote_bda);
      if (p_clcb->p_srcb == NULL)
        p_clcb->p_srcb = bta_gattc_srcb_alloc(remote_bda);

      if (p_clcb->p_rcb != NULL && p_clcb->p_srcb != NULL) {
        p_clcb->p_srcb->num_clcb++;
        p_clcb->p_rcb->num_clcb++;
      } else {
        /* release this clcb if clcb or srcb allocation failed */
        p_clcb->in_use = false;
        p_clcb = NULL;
      }
      break;
    }
  }
  return p_clcb;
}
/*******************************************************************************
 *
 * Function         bta_gattc_find_alloc_clcb
 *
 * Description      find or allocate CLCB if not found.
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_find_alloc_clcb(tBTA_GATTC_IF client_if,
                                           const RawAddress& remote_bda,
                                           tBTA_TRANSPORT transport) {
  tBTA_GATTC_CLCB* p_clcb;

  p_clcb = bta_gattc_find_clcb_by_cif(client_if, remote_bda, transport);
  if (p_clcb == NULL) {
    p_clcb = bta_gattc_clcb_alloc(client_if, remote_bda, transport);
  }
  return p_clcb;
}

/*******************************************************************************
 *
 * Function         bta_gattc_clcb_dealloc
 *
 * Description      Deallocte a clcb
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
void bta_gattc_clcb_dealloc(tBTA_GATTC_CLCB* p_clcb) {
  tBTA_GATTC_SERV* p_srcb = NULL;

  if (p_clcb) {
    p_srcb = p_clcb->p_srcb;
    if (p_srcb->num_clcb) p_srcb->num_clcb--;

    if (p_clcb->p_rcb->num_clcb) p_clcb->p_rcb->num_clcb--;

    /* if the srcb is no longer needed, reset the state */
    if (p_srcb->num_clcb == 0) {
      p_srcb->connected = false;
      p_srcb->state = BTA_GATTC_SERV_IDLE;
      p_srcb->mtu = 0;

      /* clean up cache */
      if (p_srcb->p_srvc_cache) {
        list_free(p_srcb->p_srvc_cache);
        p_srcb->p_srvc_cache = NULL;
      }
    }

    osi_free_and_reset((void**)&p_clcb->p_q_cmd);
    memset(p_clcb, 0, sizeof(tBTA_GATTC_CLCB));
  } else {
    APPL_TRACE_ERROR("bta_gattc_clcb_dealloc p_clcb=NULL");
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_find_srcb
 *
 * Description      find server cache by remote bd address currently in use
 *
 * Returns          pointer to the server cache.
 *
 ******************************************************************************/
tBTA_GATTC_SERV* bta_gattc_find_srcb(const RawAddress& bda) {
  tBTA_GATTC_SERV* p_srcb = &bta_gattc_cb.known_server[0];
  uint8_t i;

  for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX; i++, p_srcb++) {
    if (p_srcb->in_use && p_srcb->server_bda == bda) return p_srcb;
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         bta_gattc_find_srvr_cache
 *
 * Description      find server cache by remote bd address
 *
 * Returns          pointer to the server cache.
 *
 ******************************************************************************/
tBTA_GATTC_SERV* bta_gattc_find_srvr_cache(const RawAddress& bda) {
  tBTA_GATTC_SERV* p_srcb = &bta_gattc_cb.known_server[0];
  uint8_t i;

  for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX; i++, p_srcb++) {
    if (p_srcb->server_bda == bda) return p_srcb;
  }
  return NULL;
}
/*******************************************************************************
 *
 * Function         bta_gattc_find_scb_by_cid
 *
 * Description      find server control block by connection ID
 *
 * Returns          pointer to the server cache.
 *
 ******************************************************************************/
tBTA_GATTC_SERV* bta_gattc_find_scb_by_cid(uint16_t conn_id) {
  tBTA_GATTC_CLCB* p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  if (p_clcb)
    return p_clcb->p_srcb;
  else
    return NULL;
}
/*******************************************************************************
 *
 * Function         bta_gattc_srcb_alloc
 *
 * Description      allocate server cache control block
 *
 * Returns          pointer to the server cache.
 *
 ******************************************************************************/
tBTA_GATTC_SERV* bta_gattc_srcb_alloc(const RawAddress& bda) {
  tBTA_GATTC_SERV *p_tcb = &bta_gattc_cb.known_server[0], *p_recycle = NULL;
  bool found = false;
  uint8_t i;

  for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX; i++, p_tcb++) {
    if (!p_tcb->in_use) {
      found = true;
      break;
    } else if (!p_tcb->connected) {
      p_recycle = p_tcb;
    }
  }

  /* if not found, try to recycle one known device */
  if (!found && !p_recycle)
    p_tcb = NULL;
  else if (!found && p_recycle)
    p_tcb = p_recycle;

  if (p_tcb != NULL) {
    if (p_tcb->p_srvc_cache != NULL) list_free(p_tcb->p_srvc_cache);

    osi_free_and_reset((void**)&p_tcb->p_srvc_list);
    memset(p_tcb, 0, sizeof(tBTA_GATTC_SERV));

    p_tcb->in_use = true;
    p_tcb->server_bda = bda;
  }
  return p_tcb;
}
/*******************************************************************************
 *
 * Function         bta_gattc_enqueue
 *
 * Description      enqueue a client request in clcb.
 *
 * Returns          success or failure.
 *
 ******************************************************************************/
bool bta_gattc_enqueue(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  if (p_clcb->p_q_cmd == NULL) {
    p_clcb->p_q_cmd = p_data;
    return true;
  }

  APPL_TRACE_ERROR("%s: already has a pending command!!", __func__);
  /* skip the callback now. ----- need to send callback ? */
  return false;
}

/*******************************************************************************
 *
 * Function         bta_gattc_check_notif_registry
 *
 * Description      check if the service notificaition has been registered.
 *
 * Returns
 *
 ******************************************************************************/
bool bta_gattc_check_notif_registry(tBTA_GATTC_RCB* p_clreg,
                                    tBTA_GATTC_SERV* p_srcb,
                                    tBTA_GATTC_NOTIFY* p_notify) {
  uint8_t i;

  for (i = 0; i < BTA_GATTC_NOTIF_REG_MAX; i++) {
    if (p_clreg->notif_reg[i].in_use &&
        p_clreg->notif_reg[i].remote_bda == p_srcb->server_bda &&
        p_clreg->notif_reg[i].handle == p_notify->handle) {
      APPL_TRACE_DEBUG("Notification registered!");
      return true;
    }
  }
  return false;
}
/*******************************************************************************
 *
 * Function         bta_gattc_clear_notif_registration
 *
 * Description      Clear up the notification registration information by
 *                  RawAddress.
 *                  Where handle is between start_handle and end_handle, and
 *                  start_handle and end_handle are boundaries of service
 *                  containing characteristic.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_clear_notif_registration(tBTA_GATTC_SERV* p_srcb,
                                        uint16_t conn_id, uint16_t start_handle,
                                        uint16_t end_handle) {
  RawAddress remote_bda;
  tBTA_GATTC_IF gatt_if;
  tBTA_GATTC_RCB* p_clrcb;
  uint8_t i;
  tGATT_TRANSPORT transport;
  uint16_t handle;

  if (GATT_GetConnectionInfor(conn_id, &gatt_if, remote_bda, &transport)) {
    p_clrcb = bta_gattc_cl_get_regcb(gatt_if);
    if (p_clrcb != NULL) {
      for (i = 0; i < BTA_GATTC_NOTIF_REG_MAX; i++) {
        if (p_clrcb->notif_reg[i].in_use &&
            p_clrcb->notif_reg[i].remote_bda == remote_bda) {
          /* It's enough to get service or characteristic handle, as
           * clear boundaries are always around service.
           */
          handle = p_clrcb->notif_reg[i].handle;
          if (handle >= start_handle && handle <= end_handle)
            memset(&p_clrcb->notif_reg[i], 0, sizeof(tBTA_GATTC_NOTIF_REG));
        }
      }
    }
  } else {
    APPL_TRACE_ERROR(
        "can not clear indication/notif registration for unknown app");
  }
  return;
}

/*******************************************************************************
 *
 * Function         bta_gattc_mark_bg_conn
 *
 * Description      mark background connection status when a bg connection is
 *                  initiated or terminated.
 *
 * Returns          true if success; false otherwise.
 *
 ******************************************************************************/
bool bta_gattc_mark_bg_conn(tBTA_GATTC_IF client_if,
                            const RawAddress& remote_bda_ptr, bool add) {
  tBTA_GATTC_BG_TCK* p_bg_tck = &bta_gattc_cb.bg_track[0];
  uint8_t i = 0;
  tBTA_GATTC_CIF_MASK* p_cif_mask;

  for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX; i++, p_bg_tck++) {
    if (p_bg_tck->in_use && ((p_bg_tck->remote_bda == remote_bda_ptr) ||
                             (p_bg_tck->remote_bda.IsEmpty()))) {
      p_cif_mask = &p_bg_tck->cif_mask;

      if (add) /* mask on the cif bit */
        *p_cif_mask |= (1 << (client_if - 1));
      else {
        if (client_if != 0)
          *p_cif_mask &= (~(1 << (client_if - 1)));
        else
          *p_cif_mask = 0;
      }
      /* no BG connection for this device, make it available */
      if (p_bg_tck->cif_mask == 0) {
        memset(p_bg_tck, 0, sizeof(tBTA_GATTC_BG_TCK));
      }
      return true;
    }
  }
  if (!add) {
    LOG(ERROR) << __func__ << " unable to find the bg connection mask for: "
               << remote_bda_ptr;
    return false;
  } else /* adding a new device mask */
  {
    for (i = 0, p_bg_tck = &bta_gattc_cb.bg_track[0];
         i < BTA_GATTC_KNOWN_SR_MAX; i++, p_bg_tck++) {
      if (!p_bg_tck->in_use) {
        p_bg_tck->in_use = true;
        p_bg_tck->remote_bda = remote_bda_ptr;

        p_cif_mask = &p_bg_tck->cif_mask;

        *p_cif_mask = (1 << (client_if - 1));
        return true;
      }
    }
    APPL_TRACE_ERROR("no available space to mark the bg connection status");
    return false;
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_check_bg_conn
 *
 * Description      check if this is a background connection background
 *                  connection.
 *
 * Returns          true if success; false otherwise.
 *
 ******************************************************************************/
bool bta_gattc_check_bg_conn(tBTA_GATTC_IF client_if,
                             const RawAddress& remote_bda, uint8_t role) {
  tBTA_GATTC_BG_TCK* p_bg_tck = &bta_gattc_cb.bg_track[0];
  uint8_t i = 0;
  bool is_bg_conn = false;

  for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX && !is_bg_conn; i++, p_bg_tck++) {
    if (p_bg_tck->in_use && (p_bg_tck->remote_bda == remote_bda ||
                             p_bg_tck->remote_bda.IsEmpty())) {
      if (((p_bg_tck->cif_mask & (1 << (client_if - 1))) != 0) &&
          role == HCI_ROLE_MASTER)
        is_bg_conn = true;
    }
  }
  return is_bg_conn;
}
/*******************************************************************************
 *
 * Function         bta_gattc_send_open_cback
 *
 * Description      send open callback
 *
 * Returns
 *
 ******************************************************************************/
void bta_gattc_send_open_cback(tBTA_GATTC_RCB* p_clreg, tBTA_GATT_STATUS status,
                               const RawAddress& remote_bda, uint16_t conn_id,
                               tBTA_TRANSPORT transport, uint16_t mtu) {
  tBTA_GATTC cb_data;

  if (p_clreg->p_cback) {
    memset(&cb_data, 0, sizeof(tBTA_GATTC));

    cb_data.open.status = status;
    cb_data.open.client_if = p_clreg->client_if;
    cb_data.open.conn_id = conn_id;
    cb_data.open.mtu = mtu;
    cb_data.open.transport = transport;
    cb_data.open.remote_bda = remote_bda;

    (*p_clreg->p_cback)(BTA_GATTC_OPEN_EVT, &cb_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_conn_alloc
 *
 * Description      allocate connection tracking spot
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CONN* bta_gattc_conn_alloc(const RawAddress& remote_bda) {
  uint8_t i_conn = 0;
  tBTA_GATTC_CONN* p_conn = &bta_gattc_cb.conn_track[0];

  for (i_conn = 0; i_conn < BTA_GATTC_CONN_MAX; i_conn++, p_conn++) {
    if (!p_conn->in_use) {
#if (BTA_GATT_DEBUG == TRUE)
      APPL_TRACE_DEBUG("bta_gattc_conn_alloc: found conn_track[%d] available",
                       i_conn);
#endif
      p_conn->in_use = true;
      p_conn->remote_bda = remote_bda;
      return p_conn;
    }
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         bta_gattc_conn_find
 *
 * Description      allocate connection tracking spot
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CONN* bta_gattc_conn_find(const RawAddress& remote_bda) {
  uint8_t i_conn = 0;
  tBTA_GATTC_CONN* p_conn = &bta_gattc_cb.conn_track[0];

  for (i_conn = 0; i_conn < BTA_GATTC_CONN_MAX; i_conn++, p_conn++) {
    if (p_conn->in_use && remote_bda == p_conn->remote_bda) {
#if (BTA_GATT_DEBUG == TRUE)
      APPL_TRACE_DEBUG("bta_gattc_conn_find: found conn_track[%d] matched",
                       i_conn);
#endif
      return p_conn;
    }
  }
  return NULL;
}

/*******************************************************************************
 *
 * Function         bta_gattc_conn_find_alloc
 *
 * Description      find or allocate connection tracking spot
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CONN* bta_gattc_conn_find_alloc(const RawAddress& remote_bda) {
  tBTA_GATTC_CONN* p_conn = bta_gattc_conn_find(remote_bda);

  if (p_conn == NULL) {
    p_conn = bta_gattc_conn_alloc(remote_bda);
  }
  return p_conn;
}

/*******************************************************************************
 *
 * Function         bta_gattc_conn_dealloc
 *
 * Description      de-allocate connection tracking spot
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
bool bta_gattc_conn_dealloc(const RawAddress& remote_bda) {
  tBTA_GATTC_CONN* p_conn = bta_gattc_conn_find(remote_bda);

  if (p_conn != NULL) {
    p_conn->in_use = false;
    p_conn->remote_bda = RawAddress::kEmpty;
    return true;
  }
  return false;
}

/*******************************************************************************
 *
 * Function         bta_gattc_find_int_conn_clcb
 *
 * Description      try to locate a clcb when an internal connecion event
 *                  arrives.
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_find_int_conn_clcb(tBTA_GATTC_DATA* p_msg) {
  tBTA_GATTC_CLCB* p_clcb = NULL;

  if (p_msg->int_conn.role == HCI_ROLE_SLAVE)
    bta_gattc_conn_find_alloc(p_msg->int_conn.remote_bda);

  /* try to locate a logic channel */
  p_clcb = bta_gattc_find_clcb_by_cif(p_msg->int_conn.client_if,
                                      p_msg->int_conn.remote_bda,
                                      p_msg->int_conn.transport);
  if (p_clcb == NULL) {
    /* for a background connection or listening connection */
    if (/*p_msg->int_conn.role == HCI_ROLE_SLAVE ||  */
        bta_gattc_check_bg_conn(p_msg->int_conn.client_if,
                                p_msg->int_conn.remote_bda,
                                p_msg->int_conn.role)) {
      /* allocate a new channel */
      p_clcb = bta_gattc_clcb_alloc(p_msg->int_conn.client_if,
                                    p_msg->int_conn.remote_bda,
                                    p_msg->int_conn.transport);
    }
  }
  return p_clcb;
}

/*******************************************************************************
 *
 * Function         bta_gattc_find_int_disconn_clcb
 *
 * Description      try to locate a clcb when an internal disconnect callback
 *                  arrives.
 *
 * Returns          pointer to the clcb
 *
 ******************************************************************************/
tBTA_GATTC_CLCB* bta_gattc_find_int_disconn_clcb(tBTA_GATTC_DATA* p_msg) {
  tBTA_GATTC_CLCB* p_clcb = NULL;

  bta_gattc_conn_dealloc(p_msg->int_conn.remote_bda);
  p_clcb = bta_gattc_find_clcb_by_conn_id(p_msg->int_conn.hdr.layer_specific);
  if (p_clcb == NULL) {
    /* connection attempt failed, send connection callback event */
    p_clcb = bta_gattc_find_clcb_by_cif(p_msg->int_conn.client_if,
                                        p_msg->int_conn.remote_bda,
                                        p_msg->int_conn.transport);
  }
  if (p_clcb == NULL) {
    APPL_TRACE_DEBUG(" disconnection ID: [%d] not used by BTA",
                     p_msg->int_conn.hdr.layer_specific);
  }
  return p_clcb;
}
