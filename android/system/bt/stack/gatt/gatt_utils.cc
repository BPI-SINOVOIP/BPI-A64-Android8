/******************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
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
 *  this file contains GATT utility functions
 *
 ******************************************************************************/
#include "bt_target.h"
#include "bt_utils.h"
#include "osi/include/osi.h"

#include <string.h>
#include "bt_common.h"
#include "stdio.h"

#include "btm_int.h"
#include "gatt_api.h"
#include "gatt_int.h"
#include "gattdefs.h"
#include "l2cdefs.h"
#include "sdp_api.h"

using base::StringPrintf;

/* check if [x, y] and [a, b] have overlapping range */
#define GATT_VALIDATE_HANDLE_RANGE(x, y, a, b) ((y) >= (a) && (x) <= (b))

#define GATT_GET_NEXT_VALID_HANDLE(x) (((x) / 10 + 1) * 10)

const char* const op_code_name[] = {"UNKNOWN",
                                    "ATT_RSP_ERROR",
                                    "ATT_REQ_MTU",
                                    "ATT_RSP_MTU",
                                    "ATT_REQ_READ_INFO",
                                    "ATT_RSP_READ_INFO",
                                    "ATT_REQ_FIND_TYPE_VALUE",
                                    "ATT_RSP_FIND_TYPE_VALUE",
                                    "ATT_REQ_READ_BY_TYPE",
                                    "ATT_RSP_READ_BY_TYPE",
                                    "ATT_REQ_READ",
                                    "ATT_RSP_READ",
                                    "ATT_REQ_READ_BLOB",
                                    "ATT_RSP_READ_BLOB",
                                    "GATT_REQ_READ_MULTI",
                                    "GATT_RSP_READ_MULTI",
                                    "GATT_REQ_READ_BY_GRP_TYPE",
                                    "GATT_RSP_READ_BY_GRP_TYPE",
                                    "ATT_REQ_WRITE",
                                    "ATT_RSP_WRITE",
                                    "ATT_CMD_WRITE",
                                    "ATT_SIGN_CMD_WRITE",
                                    "ATT_REQ_PREPARE_WRITE",
                                    "ATT_RSP_PREPARE_WRITE",
                                    "ATT_REQ_EXEC_WRITE",
                                    "ATT_RSP_EXEC_WRITE",
                                    "Reserved",
                                    "ATT_HANDLE_VALUE_NOTIF",
                                    "Reserved",
                                    "ATT_HANDLE_VALUE_IND",
                                    "ATT_HANDLE_VALUE_CONF",
                                    "ATT_OP_CODE_MAX"};

static const uint8_t base_uuid[LEN_UUID_128] = {
    0xFB, 0x34, 0x9B, 0x5F, 0x80, 0x00, 0x00, 0x80,
    0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

/*******************************************************************************
 *
 * Function         gatt_free_pending_ind
 *
 * Description    Free all pending indications
 *
 * Returns       None
 *
 ******************************************************************************/
void gatt_free_pending_ind(tGATT_TCB* p_tcb) {
  VLOG(1) << __func__;

  if (p_tcb->pending_ind_q == NULL) return;

  /* release all queued indications */
  while (!fixed_queue_is_empty(p_tcb->pending_ind_q))
    osi_free(fixed_queue_try_dequeue(p_tcb->pending_ind_q));
  fixed_queue_free(p_tcb->pending_ind_q, NULL);
  p_tcb->pending_ind_q = NULL;
}

/*******************************************************************************
 *
 * Function         gatt_delete_dev_from_srv_chg_clt_list
 *
 * Description    Delete a device from the service changed client lit
 *
 * Returns       None
 *
 ******************************************************************************/
void gatt_delete_dev_from_srv_chg_clt_list(const RawAddress& bd_addr) {
  VLOG(1) << __func__;

  tGATTS_SRV_CHG* p_buf = gatt_is_bda_in_the_srv_chg_clt_list(bd_addr);
  if (p_buf != NULL) {
    if (gatt_cb.cb_info.p_srv_chg_callback) {
      /* delete from NV */
      tGATTS_SRV_CHG_REQ req;
      req.srv_chg.bda = bd_addr;
      (*gatt_cb.cb_info.p_srv_chg_callback)(GATTS_SRV_CHG_CMD_REMOVE_CLIENT,
                                            &req, NULL);
    }
    osi_free(fixed_queue_try_remove_from_queue(gatt_cb.srv_chg_clt_q, p_buf));
  }
}

/*******************************************************************************
 *
 * Function         gatt_set_srv_chg
 *
 * Description      Set the service changed flag to true
 *
 * Returns        None
 *
 ******************************************************************************/
void gatt_set_srv_chg(void) {
  VLOG(1) << __func__;

  if (fixed_queue_is_empty(gatt_cb.srv_chg_clt_q)) return;

  list_t* list = fixed_queue_get_list(gatt_cb.srv_chg_clt_q);
  for (const list_node_t* node = list_begin(list); node != list_end(list);
       node = list_next(node)) {
    VLOG(1) << "found a srv_chg clt";

    tGATTS_SRV_CHG* p_buf = (tGATTS_SRV_CHG*)list_node(node);
    if (!p_buf->srv_changed) {
      VLOG(1) << "set srv_changed to true";
      p_buf->srv_changed = true;
      tGATTS_SRV_CHG_REQ req;
      memcpy(&req.srv_chg, p_buf, sizeof(tGATTS_SRV_CHG));
      if (gatt_cb.cb_info.p_srv_chg_callback)
        (*gatt_cb.cb_info.p_srv_chg_callback)(GATTS_SRV_CHG_CMD_UPDATE_CLIENT,
                                              &req, NULL);
    }
  }
}

/*******************************************************************************
 *
 * Function     gatt_add_pending_ind
 *
 * Description  Add a pending indication
 *
 * Returns    Pointer to the current pending indication buffer, NULL no buffer
 *            available
 *
 ******************************************************************************/
tGATT_VALUE* gatt_add_pending_ind(tGATT_TCB* p_tcb, tGATT_VALUE* p_ind) {
  tGATT_VALUE* p_buf = (tGATT_VALUE*)osi_malloc(sizeof(tGATT_VALUE));

  VLOG(1) << __func__ << "enqueue a pending indication";

  memcpy(p_buf, p_ind, sizeof(tGATT_VALUE));
  fixed_queue_enqueue(p_tcb->pending_ind_q, p_buf);

  return p_buf;
}

/*******************************************************************************
 *
 * Function     gatt_add_srv_chg_clt
 *
 * Description  Add a service chnage client to the service change client queue
 *
 * Returns    Pointer to the service change client buffer; Null no buffer
 *            available
 *
 ******************************************************************************/
tGATTS_SRV_CHG* gatt_add_srv_chg_clt(tGATTS_SRV_CHG* p_srv_chg) {
  tGATTS_SRV_CHG* p_buf = (tGATTS_SRV_CHG*)osi_malloc(sizeof(tGATTS_SRV_CHG));
  VLOG(1) << __func__ << "enqueue a srv chg client";

  memcpy(p_buf, p_srv_chg, sizeof(tGATTS_SRV_CHG));
  fixed_queue_enqueue(gatt_cb.srv_chg_clt_q, p_buf);

  return p_buf;
}

/**
 * Returns pointer to the handle range buffer starting at handle |handle|,
 * nullptr
 * if no buffer available
 */
tGATT_HDL_LIST_ELEM* gatt_find_hdl_buffer_by_handle(uint16_t handle) {
  for (auto& elem : *gatt_cb.hdl_list_info) {
    if (elem.asgn_range.s_handle == handle) return &elem;
  }

  return nullptr;
}
/*******************************************************************************
 *
 * Description  Find handle range buffer by app ID, service and service instance
 *              ID.
 *
 * Returns    Pointer to the buffer, NULL no buffer available
 *
 ******************************************************************************/
std::list<tGATT_HDL_LIST_ELEM>::iterator gatt_find_hdl_buffer_by_app_id(
    tBT_UUID* p_app_uuid128, tBT_UUID* p_svc_uuid, uint16_t start_handle) {
  auto end_it = gatt_cb.hdl_list_info->end();
  auto it = gatt_cb.hdl_list_info->begin();
  for (; it != end_it; it++) {
    if (gatt_uuid_compare(*p_app_uuid128, it->asgn_range.app_uuid128) &&
        gatt_uuid_compare(*p_svc_uuid, it->asgn_range.svc_uuid) &&
        (start_handle == it->asgn_range.s_handle)) {
      return it;
    }
  }

  return it;
}

/**
 * free the service attribute database buffers by the owner of the service app
 * ID.
 */
void gatt_free_srvc_db_buffer_app_id(tBT_UUID* p_app_id) {
  auto it = gatt_cb.hdl_list_info->begin();
  auto end = gatt_cb.hdl_list_info->end();
  while (it != end) {
    if (memcmp(p_app_id, &it->asgn_range.app_uuid128, sizeof(tBT_UUID)) == 0) {
      it = gatt_cb.hdl_list_info->erase(it);
    } else {
      it++;
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_find_the_connected_bda
 *
 * Description      This function find the connected bda
 *
 * Returns           true if found
 *
 ******************************************************************************/
bool gatt_find_the_connected_bda(uint8_t start_idx, RawAddress& bda,
                                 uint8_t* p_found_idx,
                                 tBT_TRANSPORT* p_transport) {
  uint8_t i;
  bool found = false;
  VLOG(1) << __func__ << " start_idx=" << +start_idx;

  for (i = start_idx; i < GATT_MAX_PHY_CHANNEL; i++) {
    if (gatt_cb.tcb[i].in_use && gatt_cb.tcb[i].ch_state == GATT_CH_OPEN) {
      bda = gatt_cb.tcb[i].peer_bda;
      *p_found_idx = i;
      *p_transport = gatt_cb.tcb[i].transport;
      found = true;
      VLOG(1) << " bda :" << bda;
      break;
    }
  }
  VLOG(1) << StringPrintf(" found=%d found_idx=%d", found, i);
  return found;
}

/*******************************************************************************
 *
 * Function         gatt_is_srv_chg_ind_pending
 *
 * Description      Check whether a service chnaged is in the indication pending
 *                  queue or waiting for an Ack already
 *
 * Returns         bool
 *
 ******************************************************************************/
bool gatt_is_srv_chg_ind_pending(tGATT_TCB* p_tcb) {
  bool srv_chg_ind_pending = false;

  VLOG(1) << __func__
          << " is_queue_empty=" << fixed_queue_is_empty(p_tcb->pending_ind_q);

  if (p_tcb->indicate_handle == gatt_cb.handle_of_h_r) {
    srv_chg_ind_pending = true;
  } else if (!fixed_queue_is_empty(p_tcb->pending_ind_q)) {
    list_t* list = fixed_queue_get_list(p_tcb->pending_ind_q);
    for (const list_node_t* node = list_begin(list); node != list_end(list);
         node = list_next(node)) {
      tGATT_VALUE* p_buf = (tGATT_VALUE*)list_node(node);
      if (p_buf->handle == gatt_cb.handle_of_h_r) {
        srv_chg_ind_pending = true;
        break;
      }
    }
  }

  VLOG(1) << __func__ << "srv_chg_ind_pending = %d", srv_chg_ind_pending;
  return srv_chg_ind_pending;
}

/*******************************************************************************
 *
 * Function         gatt_is_bda_in_the_srv_chg_clt_list
 *
 * Description      This function check the specified bda is in the srv chg
 *                  client list or not
 *
 * Returns         pointer to the found elemenet otherwise NULL
 *
 ******************************************************************************/
tGATTS_SRV_CHG* gatt_is_bda_in_the_srv_chg_clt_list(const RawAddress& bda) {
  tGATTS_SRV_CHG* p_buf = NULL;

  VLOG(1) << __func__ << ": " << bda;

  if (fixed_queue_is_empty(gatt_cb.srv_chg_clt_q)) return NULL;

  list_t* list = fixed_queue_get_list(gatt_cb.srv_chg_clt_q);
  for (const list_node_t* node = list_begin(list); node != list_end(list);
       node = list_next(node)) {
    tGATTS_SRV_CHG* p_buf = (tGATTS_SRV_CHG*)list_node(node);
    if (bda == p_buf->bda) {
      VLOG(1) << "bda is in the srv chg clt list";
      break;
    }
  }

  return p_buf;
}

/*******************************************************************************
 *
 * Function         gatt_is_bda_connected
 *
 * Description
 *
 * Returns          GATT_INDEX_INVALID if not found. Otherwise index to the tcb.
 *
 ******************************************************************************/
bool gatt_is_bda_connected(const RawAddress& bda) {
  uint8_t i = 0;
  bool connected = false;

  for (i = 0; i < GATT_MAX_PHY_CHANNEL; i++) {
    if (gatt_cb.tcb[i].in_use && gatt_cb.tcb[i].peer_bda == bda) {
      connected = true;
      break;
    }
  }
  return connected;
}

/*******************************************************************************
 *
 * Function         gatt_find_i_tcb_by_addr
 *
 * Description      Search for an empty tcb entry, and return the index.
 *
 * Returns          GATT_INDEX_INVALID if not found. Otherwise index to the tcb.
 *
 ******************************************************************************/
uint8_t gatt_find_i_tcb_by_addr(const RawAddress& bda,
                                tBT_TRANSPORT transport) {
  uint8_t i = 0;

  for (; i < GATT_MAX_PHY_CHANNEL; i++) {
    if (gatt_cb.tcb[i].peer_bda == bda &&
        gatt_cb.tcb[i].transport == transport) {
      return i;
    }
  }
  return GATT_INDEX_INVALID;
}

/*******************************************************************************
 *
 * Function         gatt_get_tcb_by_idx
 *
 * Description      The function get TCB using the TCB index
 *
 * Returns           NULL if not found. Otherwise index to the tcb.
 *
 ******************************************************************************/
tGATT_TCB* gatt_get_tcb_by_idx(uint8_t tcb_idx) {
  tGATT_TCB* p_tcb = NULL;

  if ((tcb_idx < GATT_MAX_PHY_CHANNEL) && gatt_cb.tcb[tcb_idx].in_use)
    p_tcb = &gatt_cb.tcb[tcb_idx];

  return p_tcb;
}

/*******************************************************************************
 *
 * Function         gatt_find_tcb_by_addr
 *
 * Description      Search for an empty tcb entry, and return pointer.
 *
 * Returns          NULL if not found. Otherwise index to the tcb.
 *
 ******************************************************************************/
tGATT_TCB* gatt_find_tcb_by_addr(const RawAddress& bda,
                                 tBT_TRANSPORT transport) {
  tGATT_TCB* p_tcb = NULL;
  uint8_t i = 0;

  i = gatt_find_i_tcb_by_addr(bda, transport);
  if (i != GATT_INDEX_INVALID) p_tcb = &gatt_cb.tcb[i];

  return p_tcb;
}

/*******************************************************************************
 *
 * Function         gatt_allocate_tcb_by_bdaddr
 *
 * Description      Locate or allocate a new tcb entry for matching bda.
 *
 * Returns          GATT_INDEX_INVALID if not found. Otherwise index to the tcb.
 *
 ******************************************************************************/
tGATT_TCB* gatt_allocate_tcb_by_bdaddr(const RawAddress& bda,
                                       tBT_TRANSPORT transport) {
  /* search for existing tcb with matching bda    */
  uint8_t j = gatt_find_i_tcb_by_addr(bda, transport);
  if (j != GATT_INDEX_INVALID) return &gatt_cb.tcb[j];

  /* find free tcb */
  for (int i = 0; i < GATT_MAX_PHY_CHANNEL; i++) {
    tGATT_TCB* p_tcb = &gatt_cb.tcb[i];
    if (p_tcb->in_use) continue;

    *p_tcb = tGATT_TCB();

    p_tcb->pending_ind_q = fixed_queue_new(SIZE_MAX);
    p_tcb->conf_timer = alarm_new("gatt.conf_timer");
    p_tcb->ind_ack_timer = alarm_new("gatt.ind_ack_timer");
    p_tcb->in_use = true;
    p_tcb->tcb_idx = i;
    p_tcb->transport = transport;
    p_tcb->peer_bda = bda;
    return p_tcb;
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_convert_uuid16_to_uuid128
 *
 * Description      Convert a 16 bits UUID to be an standard 128 bits one.
 *
 * Returns          true if two uuid match; false otherwise.
 *
 ******************************************************************************/
void gatt_convert_uuid16_to_uuid128(uint8_t uuid_128[LEN_UUID_128],
                                    uint16_t uuid_16) {
  uint8_t* p = &uuid_128[LEN_UUID_128 - 4];

  memcpy(uuid_128, base_uuid, LEN_UUID_128);

  UINT16_TO_STREAM(p, uuid_16);
}

/*******************************************************************************
 *
 * Function         gatt_convert_uuid32_to_uuid128
 *
 * Description      Convert a 32 bits UUID to be an standard 128 bits one.
 *
 * Returns          true if two uuid match; false otherwise.
 *
 ******************************************************************************/
void gatt_convert_uuid32_to_uuid128(uint8_t uuid_128[LEN_UUID_128],
                                    uint32_t uuid_32) {
  uint8_t* p = &uuid_128[LEN_UUID_128 - 4];

  memcpy(uuid_128, base_uuid, LEN_UUID_128);

  UINT32_TO_STREAM(p, uuid_32);
}
/*******************************************************************************
 *
 * Function         gatt_uuid_compare
 *
 * Description      Compare two UUID to see if they are the same.
 *
 * Returns          true if two uuid match; false otherwise.
 *
 ******************************************************************************/
bool gatt_uuid_compare(tBT_UUID src, tBT_UUID tar) {
  uint8_t su[LEN_UUID_128], tu[LEN_UUID_128];
  uint8_t *ps, *pt;

  /* any of the UUID is unspecified */
  if (src.len == 0 || tar.len == 0) {
    return true;
  }

  /* If both are 16-bit, we can do a simple compare */
  if (src.len == LEN_UUID_16 && tar.len == LEN_UUID_16) {
    return src.uu.uuid16 == tar.uu.uuid16;
  }

  /* If both are 32-bit, we can do a simple compare */
  if (src.len == LEN_UUID_32 && tar.len == LEN_UUID_32) {
    return src.uu.uuid32 == tar.uu.uuid32;
  }

  /* One or both of the UUIDs is 128-bit */
  if (src.len == LEN_UUID_16) {
    /* convert a 16 bits UUID to 128 bits value */
    gatt_convert_uuid16_to_uuid128(su, src.uu.uuid16);
    ps = su;
  } else if (src.len == LEN_UUID_32) {
    gatt_convert_uuid32_to_uuid128(su, src.uu.uuid32);
    ps = su;
  } else
    ps = src.uu.uuid128;

  if (tar.len == LEN_UUID_16) {
    /* convert a 16 bits UUID to 128 bits value */
    gatt_convert_uuid16_to_uuid128(tu, tar.uu.uuid16);
    pt = tu;
  } else if (tar.len == LEN_UUID_32) {
    /* convert a 32 bits UUID to 128 bits value */
    gatt_convert_uuid32_to_uuid128(tu, tar.uu.uuid32);
    pt = tu;
  } else
    pt = tar.uu.uuid128;

  return (memcmp(ps, pt, LEN_UUID_128) == 0);
}

/*******************************************************************************
 *
 * Function         gatt_build_uuid_to_stream
 *
 * Description      Add UUID into stream.
 *
 * Returns          UUID length.
 *
 ******************************************************************************/
uint8_t gatt_build_uuid_to_stream(uint8_t** p_dst, tBT_UUID uuid) {
  uint8_t* p = *p_dst;
  uint8_t len = 0;

  if (uuid.len == LEN_UUID_16) {
    UINT16_TO_STREAM(p, uuid.uu.uuid16);
    len = LEN_UUID_16;
  } else if (uuid.len ==
             LEN_UUID_32) /* always convert 32 bits into 128 bits as alwats */
  {
    gatt_convert_uuid32_to_uuid128(p, uuid.uu.uuid32);
    p += LEN_UUID_128;
    len = LEN_UUID_128;
  } else if (uuid.len == LEN_UUID_128) {
    ARRAY_TO_STREAM(p, uuid.uu.uuid128, LEN_UUID_128);
    len = LEN_UUID_128;
  }

  *p_dst = p;
  return len;
}

/*******************************************************************************
 *
 * Function         gatt_parse_uuid_from_cmd
 *
 * Description      Convert a 128 bits UUID into a 16 bits UUID.
 *
 * Returns          true if command sent, otherwise false.
 *
 ******************************************************************************/
bool gatt_parse_uuid_from_cmd(tBT_UUID* p_uuid_rec, uint16_t uuid_size,
                              uint8_t** p_data) {
  bool is_base_uuid, ret = true;
  uint8_t xx;
  uint8_t* p_uuid = *p_data;

  memset(p_uuid_rec, 0, sizeof(tBT_UUID));

  switch (uuid_size) {
    case LEN_UUID_16:
      p_uuid_rec->len = uuid_size;
      STREAM_TO_UINT16(p_uuid_rec->uu.uuid16, p_uuid);
      *p_data += LEN_UUID_16;
      break;

    case LEN_UUID_128:
      /* See if we can compress his UUID down to 16 or 32bit UUIDs */
      is_base_uuid = true;
      for (xx = 0; xx < LEN_UUID_128 - 4; xx++) {
        if (p_uuid[xx] != base_uuid[xx]) {
          is_base_uuid = false;
          break;
        }
      }
      if (is_base_uuid) {
        if ((p_uuid[LEN_UUID_128 - 1] == 0) &&
            (p_uuid[LEN_UUID_128 - 2] == 0)) {
          p_uuid += (LEN_UUID_128 - 4);
          p_uuid_rec->len = LEN_UUID_16;
          STREAM_TO_UINT16(p_uuid_rec->uu.uuid16, p_uuid);
        } else {
          p_uuid += (LEN_UUID_128 - LEN_UUID_32);
          p_uuid_rec->len = LEN_UUID_32;
          STREAM_TO_UINT32(p_uuid_rec->uu.uuid32, p_uuid);
        }
      }
      if (!is_base_uuid) {
        p_uuid_rec->len = LEN_UUID_128;
        memcpy(p_uuid_rec->uu.uuid128, p_uuid, LEN_UUID_128);
      }
      *p_data += LEN_UUID_128;
      break;

    /* do not allow 32 bits UUID in ATT PDU now */
    case LEN_UUID_32:
      LOG(ERROR) << "DO NOT ALLOW 32 BITS UUID IN ATT PDU";
      return false;
    case 0:
    default:
      if (uuid_size != 0) ret = false;
      LOG(WARNING) << __func__ << ": invalid uuid size";
      break;
  }

  return (ret);
}

/*******************************************************************************
 *
 * Function         gatt_start_rsp_timer
 *
 * Description      Start a wait_for_response timer.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_start_rsp_timer(tGATT_CLCB* p_clcb) {
  period_ms_t timeout_ms = GATT_WAIT_FOR_RSP_TIMEOUT_MS;

  if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
      p_clcb->op_subtype == GATT_DISC_SRVC_ALL) {
    timeout_ms = GATT_WAIT_FOR_DISC_RSP_TIMEOUT_MS;
  }

  // TODO: The tGATT_CLCB memory and state management needs cleanup,
  // and then the timers can be allocated elsewhere.
  if (p_clcb->gatt_rsp_timer_ent == NULL) {
    p_clcb->gatt_rsp_timer_ent = alarm_new("gatt.gatt_rsp_timer_ent");
  }
  alarm_set_on_mloop(p_clcb->gatt_rsp_timer_ent, timeout_ms, gatt_rsp_timeout,
                     p_clcb);
}

/*******************************************************************************
 *
 * Function         gatt_start_conf_timer
 *
 * Description      Start a wait_for_confirmation timer.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_start_conf_timer(tGATT_TCB* p_tcb) {
  alarm_set_on_mloop(p_tcb->conf_timer, GATT_WAIT_FOR_RSP_TIMEOUT_MS,
                     gatt_indication_confirmation_timeout, p_tcb);
}

/*******************************************************************************
 *
 * Function         gatt_start_ind_ack_timer
 *
 * Description      start the application ack timer
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_start_ind_ack_timer(tGATT_TCB& tcb) {
  /* start notification cache timer */
  alarm_set_on_mloop(tcb.ind_ack_timer, GATT_WAIT_FOR_RSP_TIMEOUT_MS,
                     gatt_ind_ack_timeout, &tcb);
}

/*******************************************************************************
 *
 * Function         gatt_rsp_timeout
 *
 * Description      Called when GATT wait for ATT command response timer expires
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_rsp_timeout(void* data) {
  tGATT_CLCB* p_clcb = (tGATT_CLCB*)data;

  if (p_clcb == NULL || p_clcb->p_tcb == NULL) {
    LOG(WARNING) << __func__ << " clcb is already deleted";
    return;
  }
  if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY &&
      p_clcb->op_subtype == GATT_DISC_SRVC_ALL &&
      p_clcb->retry_count < GATT_REQ_RETRY_LIMIT) {
    uint8_t rsp_code;
    LOG(WARNING) << __func__ << " retry discovery primary service";
    if (p_clcb != gatt_cmd_dequeue(*p_clcb->p_tcb, &rsp_code)) {
      LOG(ERROR) << __func__ << " command queue out of sync, disconnect";
    } else {
      p_clcb->retry_count++;
      gatt_act_discovery(p_clcb);
      return;
    }
  }

  LOG(WARNING) << __func__ << " disconnecting...";
  gatt_disconnect(p_clcb->p_tcb);
}

/*******************************************************************************
 *
 * Function         gatt_indication_confirmation_timeout
 *
 * Description      Called when the indication confirmation timer expires
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_indication_confirmation_timeout(void* data) {
  tGATT_TCB* p_tcb = (tGATT_TCB*)data;

  LOG(WARNING) << __func__ << " disconnecting...";
  gatt_disconnect(p_tcb);
}

/*******************************************************************************
 *
 * Function         gatt_ind_ack_timeout
 *
 * Description      Called when GATT wait for ATT handle confirmation timeout
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_ind_ack_timeout(void* data) {
  tGATT_TCB* p_tcb = (tGATT_TCB*)data;
  CHECK(p_tcb);

  LOG(WARNING) << __func__ << ": send ack now";
  p_tcb->ind_count = 0;
  attp_send_cl_msg(*p_tcb, nullptr, GATT_HANDLE_VALUE_CONF, NULL);
}
/*******************************************************************************
 *
 * Description      Search for a service that owns a specific handle.
 *
 * Returns          GATT_MAX_SR_PROFILES if not found. Otherwise the index of
 *                  the service.
 *
 ******************************************************************************/
std::list<tGATT_SRV_LIST_ELEM>::iterator gatt_sr_find_i_rcb_by_handle(
    uint16_t handle) {
  auto it = gatt_cb.srv_list_info->begin();

  for (; it != gatt_cb.srv_list_info->end(); it++) {
    if (it->s_hdl <= handle && it->e_hdl >= handle) {
      return it;
    }
  }

  return it;
}

/*******************************************************************************
 *
 * Function         gatt_sr_get_sec_info
 *
 * Description      Get the security flag and key size information for the peer
 *                  device.
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_sr_get_sec_info(const RawAddress& rem_bda, tBT_TRANSPORT transport,
                          uint8_t* p_sec_flag, uint8_t* p_key_size) {
  uint8_t sec_flag = 0;

  BTM_GetSecurityFlagsByTransport(rem_bda, &sec_flag, transport);

  sec_flag &= (GATT_SEC_FLAG_LKEY_UNAUTHED | GATT_SEC_FLAG_LKEY_AUTHED |
               GATT_SEC_FLAG_ENCRYPTED);

  *p_key_size = btm_ble_read_sec_key_size(rem_bda);
  *p_sec_flag = sec_flag;
}
/*******************************************************************************
 *
 * Function         gatt_sr_send_req_callback
 *
 * Description
 *
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_sr_send_req_callback(uint16_t conn_id, uint32_t trans_id,
                               tGATTS_REQ_TYPE type, tGATTS_DATA* p_data) {
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  if (!p_reg) {
    LOG(ERROR) << "p_reg not found discard request";
    return;
  }

  if (p_reg->in_use && p_reg->app_cb.p_req_cb) {
    (*p_reg->app_cb.p_req_cb)(conn_id, trans_id, type, p_data);
  } else {
    LOG(WARNING) << "Call back not found for application conn_id=" << conn_id;
  }
}

/*******************************************************************************
 *
 * Function         gatt_send_error_rsp
 *
 * Description      This function sends an error response.
 *
 * Returns          void
 *
 ******************************************************************************/
tGATT_STATUS gatt_send_error_rsp(tGATT_TCB& tcb, uint8_t err_code,
                                 uint8_t op_code, uint16_t handle, bool deq) {
  tGATT_STATUS status;
  BT_HDR* p_buf;

  tGATT_SR_MSG msg;
  msg.error.cmd_code = op_code;
  msg.error.reason = err_code;
  msg.error.handle = handle;

  p_buf = attp_build_sr_msg(tcb, GATT_RSP_ERROR, &msg);
  if (p_buf != NULL) {
    status = attp_send_sr_msg(tcb, p_buf);
  } else
    status = GATT_INSUF_RESOURCE;

  if (deq) gatt_dequeue_sr_cmd(tcb);

  return status;
}

/*******************************************************************************
 *
 * Function         gatt_add_sdp_record
 *
 * Description      This function add a SDP record for a GATT primary service
 *
 * Returns          0 if error else sdp handle for the record.
 *
 ******************************************************************************/
uint32_t gatt_add_sdp_record(tBT_UUID* p_uuid, uint16_t start_hdl,
                             uint16_t end_hdl) {
  tSDP_PROTOCOL_ELEM proto_elem_list[2];
  uint32_t sdp_handle;
  uint16_t list = UUID_SERVCLASS_PUBLIC_BROWSE_GROUP;
  uint8_t buff[60];
  uint8_t* p = buff;

  VLOG(1) << __func__
          << StringPrintf(" s_hdl=0x%x  s_hdl=0x%x", start_hdl, end_hdl);

  sdp_handle = SDP_CreateRecord();
  if (sdp_handle == 0) return 0;

  switch (p_uuid->len) {
    case LEN_UUID_16:
      SDP_AddServiceClassIdList(sdp_handle, 1, &p_uuid->uu.uuid16);
      break;

    case LEN_UUID_32:
      UINT8_TO_BE_STREAM(p, (UUID_DESC_TYPE << 3) | SIZE_FOUR_BYTES);
      UINT32_TO_BE_STREAM(p, p_uuid->uu.uuid32);
      SDP_AddAttribute(sdp_handle, ATTR_ID_SERVICE_CLASS_ID_LIST,
                       DATA_ELE_SEQ_DESC_TYPE, (uint32_t)(p - buff), buff);
      break;

    case LEN_UUID_128:
      UINT8_TO_BE_STREAM(p, (UUID_DESC_TYPE << 3) | SIZE_SIXTEEN_BYTES);
      ARRAY_TO_BE_STREAM_REVERSE(p, p_uuid->uu.uuid128, LEN_UUID_128);
      SDP_AddAttribute(sdp_handle, ATTR_ID_SERVICE_CLASS_ID_LIST,
                       DATA_ELE_SEQ_DESC_TYPE, (uint32_t)(p - buff), buff);
      break;

    default:
      LOG(ERROR) << "inavlid UUID len=" << +p_uuid->len;
      SDP_DeleteRecord(sdp_handle);
      return 0;
      break;
  }

  /*** Fill out the protocol element sequence for SDP ***/
  proto_elem_list[0].protocol_uuid = UUID_PROTOCOL_L2CAP;
  proto_elem_list[0].num_params = 1;
  proto_elem_list[0].params[0] = BT_PSM_ATT;
  proto_elem_list[1].protocol_uuid = UUID_PROTOCOL_ATT;
  proto_elem_list[1].num_params = 2;
  proto_elem_list[1].params[0] = start_hdl;
  proto_elem_list[1].params[1] = end_hdl;

  SDP_AddProtocolList(sdp_handle, 2, proto_elem_list);

  /* Make the service browseable */
  SDP_AddUuidSequence(sdp_handle, ATTR_ID_BROWSE_GROUP_LIST, 1, &list);

  return (sdp_handle);
}

#if GATT_CONFORMANCE_TESTING == TRUE
/*******************************************************************************
 *
 * Function         gatt_set_err_rsp
 *
 * Description      This function is called to set the test confirm value
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_set_err_rsp(bool enable, uint8_t req_op_code, uint8_t err_status) {
  VLOG(1) << __func__ << StringPrintf(" enable=%d op_code=%d, err_status=%d",
                                      enable, req_op_code, err_status);
  gatt_cb.enable_err_rsp = enable;
  gatt_cb.req_op_code = req_op_code;
  gatt_cb.err_status = err_status;
}
#endif

/*******************************************************************************
 *
 * Function         gatt_get_regcb
 *
 * Description      The function returns the registration control block.
 *
 * Returns          pointer to the registration control block or NULL
 *
 ******************************************************************************/
tGATT_REG* gatt_get_regcb(tGATT_IF gatt_if) {
  uint8_t ii = (uint8_t)gatt_if;
  tGATT_REG* p_reg = NULL;

  if (ii < 1 || ii > GATT_MAX_APPS) {
    LOG(WARNING) << "gatt_if out of range = " << +ii;
    return NULL;
  }

  // Index for cl_rcb is always 1 less than gatt_if.
  p_reg = &gatt_cb.cl_rcb[ii - 1];

  if (!p_reg->in_use) {
    LOG(WARNING) << "gatt_if found but not in use.";
    return NULL;
  }

  return p_reg;
}

/*******************************************************************************
 *
 * Function         gatt_is_clcb_allocated
 *
 * Description      The function check clcb for conn_id is allocated or not
 *
 * Returns           True already allocated
 *
 ******************************************************************************/

bool gatt_is_clcb_allocated(uint16_t conn_id) {
  uint8_t i = 0;
  bool is_allocated = false;

  for (i = 0; i < GATT_CL_MAX_LCB; i++) {
    if (gatt_cb.clcb[i].in_use && (gatt_cb.clcb[i].conn_id == conn_id)) {
      is_allocated = true;
      break;
    }
  }

  return is_allocated;
}

/*******************************************************************************
 *
 * Function         gatt_clcb_alloc
 *
 * Description      The function allocates a GATT  connection link control block
 *
 * Returns          NULL if not found. Otherwise pointer to the connection link
 *                  block.
 *
 ******************************************************************************/
tGATT_CLCB* gatt_clcb_alloc(uint16_t conn_id) {
  uint8_t i = 0;
  tGATT_CLCB* p_clcb = NULL;
  tGATT_IF gatt_if = GATT_GET_GATT_IF(conn_id);
  uint8_t tcb_idx = GATT_GET_TCB_IDX(conn_id);
  tGATT_TCB* p_tcb = gatt_get_tcb_by_idx(tcb_idx);
  tGATT_REG* p_reg = gatt_get_regcb(gatt_if);

  for (i = 0; i < GATT_CL_MAX_LCB; i++) {
    if (!gatt_cb.clcb[i].in_use) {
      p_clcb = &gatt_cb.clcb[i];

      p_clcb->in_use = true;
      p_clcb->conn_id = conn_id;
      p_clcb->p_reg = p_reg;
      p_clcb->p_tcb = p_tcb;
      break;
    }
  }

  return p_clcb;
}

/*******************************************************************************
 *
 * Function         gatt_clcb_dealloc
 *
 * Description      The function de-allocates a GATT connection link control
 *                  block
 *
 * Returns         None
 *
 ******************************************************************************/
void gatt_clcb_dealloc(tGATT_CLCB* p_clcb) {
  if (p_clcb && p_clcb->in_use) {
    alarm_free(p_clcb->gatt_rsp_timer_ent);
    memset(p_clcb, 0, sizeof(tGATT_CLCB));
  }
}

/*******************************************************************************
 *
 * Function         gatt_find_tcb_by_cid
 *
 * Description      The function searches for an empty entry
 *                   in registration info table for GATT client
 *
 * Returns           NULL if not found. Otherwise pointer to the rcb.
 *
 ******************************************************************************/
tGATT_TCB* gatt_find_tcb_by_cid(uint16_t lcid) {
  uint16_t xx = 0;
  tGATT_TCB* p_tcb = NULL;

  for (xx = 0; xx < GATT_MAX_PHY_CHANNEL; xx++) {
    if (gatt_cb.tcb[xx].in_use && gatt_cb.tcb[xx].att_lcid == lcid) {
      p_tcb = &gatt_cb.tcb[xx];
      break;
    }
  }
  return p_tcb;
}

/*******************************************************************************
 *
 * Function         gatt_num_clcb_by_bd_addr
 *
 * Description      The function searches all LCB with macthing bd address
 *
 * Returns          total number of clcb found.
 *
 ******************************************************************************/
uint8_t gatt_num_clcb_by_bd_addr(const RawAddress& bda) {
  uint8_t i, num = 0;

  for (i = 0; i < GATT_CL_MAX_LCB; i++) {
    if (gatt_cb.clcb[i].in_use && gatt_cb.clcb[i].p_tcb->peer_bda == bda) num++;
  }
  return num;
}

void gatt_sr_copy_prep_cnt_to_cback_cnt(tGATT_TCB& tcb) {
  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    if (tcb.prep_cnt[i]) {
      tcb.sr_cmd.cback_cnt[i] = 1;
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_cback_cnt_zero
 *
 * Description      The function searches all LCB with macthing bd address
 *
 * Returns          True if thetotal application callback count is zero
 *
 ******************************************************************************/
bool gatt_sr_is_cback_cnt_zero(tGATT_TCB& tcb) {
  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    if (tcb.sr_cmd.cback_cnt[i]) {
      return false;
    }
  }
  return true;
}

/*******************************************************************************
 *
 * Function         gatt_sr_is_prep_cnt_zero
 *
 * Description      Check the prepare write request count is zero or not
 *
 * Returns          True no prepare write request
 *
 ******************************************************************************/
bool gatt_sr_is_prep_cnt_zero(tGATT_TCB& tcb) {
  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    if (tcb.prep_cnt[i]) {
      return false;
    }
  }
  return true;
}

/*******************************************************************************
 *
 * Function         gatt_sr_reset_cback_cnt
 *
 * Description      Reset the application callback count to zero
 *
 * Returns         None
 *
 ******************************************************************************/
void gatt_sr_reset_cback_cnt(tGATT_TCB& tcb) {
  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    tcb.sr_cmd.cback_cnt[i] = 0;
  }
}

/*******************************************************************************
 *
 * Function         gatt_sr_reset_prep_cnt
 *
 * Description     Reset the prep write count to zero
 *
 * Returns        None
 *
 ******************************************************************************/
void gatt_sr_reset_prep_cnt(tGATT_TCB& tcb) {
  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    tcb.prep_cnt[i] = 0;
  }
}

/*******************************************************************************
 *
 * Function         gatt_sr_update_cback_cnt
 *
 * Description    Update the teh applicaiton callback count
 *
 * Returns           None
 *
 ******************************************************************************/
void gatt_sr_update_cback_cnt(tGATT_TCB& tcb, tGATT_IF gatt_if, bool is_inc,
                              bool is_reset_first) {
  uint8_t idx = ((uint8_t)gatt_if) - 1;

  if (is_reset_first) {
    gatt_sr_reset_cback_cnt(tcb);
  }
  if (is_inc) {
    tcb.sr_cmd.cback_cnt[idx]++;
  } else {
    if (tcb.sr_cmd.cback_cnt[idx]) {
      tcb.sr_cmd.cback_cnt[idx]--;
    }
  }
}

/*******************************************************************************
 *
 * Function         gatt_sr_update_prep_cnt
 *
 * Description    Update the teh prepare write request count
 *
 * Returns           None
 *
 ******************************************************************************/
void gatt_sr_update_prep_cnt(tGATT_TCB& tcb, tGATT_IF gatt_if, bool is_inc,
                             bool is_reset_first) {
  uint8_t idx = ((uint8_t)gatt_if) - 1;

  VLOG(1) << StringPrintf(
      "%s tcb idx=%d gatt_if=%d is_inc=%d is_reset_first=%d", __func__,
      tcb.tcb_idx, gatt_if, is_inc, is_reset_first);

  if (is_reset_first) {
    gatt_sr_reset_prep_cnt(tcb);
  }
  if (is_inc) {
    tcb.prep_cnt[idx]++;
  } else {
    if (tcb.prep_cnt[idx]) {
      tcb.prep_cnt[idx]--;
    }
  }
}
/*******************************************************************************
 *
 * Function         gatt_cancel_open
 *
 * Description      Cancel open request
 *
 * Returns         Boolean
 *
 ******************************************************************************/
bool gatt_cancel_open(tGATT_IF gatt_if, const RawAddress& bda) {
  tGATT_TCB* p_tcb = NULL;
  bool status = true;

  p_tcb = gatt_find_tcb_by_addr(bda, BT_TRANSPORT_LE);

  if (p_tcb) {
    if (gatt_get_ch_state(p_tcb) == GATT_CH_OPEN) {
      LOG(ERROR) << __func__ << ": link connected Too late to cancel";
      status = false;
    } else {
      gatt_update_app_use_link_flag(gatt_if, p_tcb, false, false);
      if (p_tcb->app_hold_link.empty()) {
        gatt_disconnect(p_tcb);
      }
    }
  }

  return status;
}

/** Enqueue this command */
void gatt_cmd_enq(tGATT_TCB& tcb, tGATT_CLCB* p_clcb, bool to_send,
                  uint8_t op_code, BT_HDR* p_buf) {
  tGATT_CMD_Q cmd;
  cmd.to_send = to_send; /* waiting to be sent */
  cmd.op_code = op_code;
  cmd.p_cmd = p_buf;
  cmd.p_clcb = p_clcb;

  if (!to_send) {
    // TODO: WTF why do we clear the queue here ?!
    tcb.cl_cmd_q = std::queue<tGATT_CMD_Q>();
  }

  tcb.cl_cmd_q.push(cmd);
}

/** dequeue the command in the client CCB command queue */
tGATT_CLCB* gatt_cmd_dequeue(tGATT_TCB& tcb, uint8_t* p_op_code) {
  if (tcb.cl_cmd_q.empty()) return nullptr;

  tGATT_CMD_Q cmd = tcb.cl_cmd_q.front();
  tGATT_CLCB* p_clcb = cmd.p_clcb;
  *p_op_code = cmd.op_code;
  tcb.cl_cmd_q.pop();

  return p_clcb;
}

/** Send out the ATT message for write */
uint8_t gatt_send_write_msg(tGATT_TCB& tcb, tGATT_CLCB* p_clcb, uint8_t op_code,
                            uint16_t handle, uint16_t len, uint16_t offset,
                            uint8_t* p_data) {
  tGATT_CL_MSG msg;
  msg.attr_value.handle = handle;
  msg.attr_value.len = len;
  msg.attr_value.offset = offset;
  memcpy(msg.attr_value.value, p_data, len);

  /* write by handle */
  return attp_send_cl_msg(tcb, p_clcb, op_code, &msg);
}

/*******************************************************************************
 *
 * Function         gatt_end_operation
 *
 * Description      This function ends a discovery, send callback and finalize
 *                  some control value.
 *
 * Returns          16 bits uuid.
 *
 ******************************************************************************/
void gatt_end_operation(tGATT_CLCB* p_clcb, tGATT_STATUS status, void* p_data) {
  tGATT_CL_COMPLETE cb_data;
  tGATT_CMPL_CBACK* p_cmpl_cb =
      (p_clcb->p_reg) ? p_clcb->p_reg->app_cb.p_cmpl_cb : NULL;
  uint8_t op = p_clcb->operation, disc_type = GATT_DISC_MAX;
  tGATT_DISC_CMPL_CB* p_disc_cmpl_cb =
      (p_clcb->p_reg) ? p_clcb->p_reg->app_cb.p_disc_cmpl_cb : NULL;
  uint16_t conn_id;
  uint8_t operation;

  VLOG(1) << __func__ << StringPrintf(" status=%d op=%d subtype=%d", status,
                                      p_clcb->operation, p_clcb->op_subtype);
  memset(&cb_data.att_value, 0, sizeof(tGATT_VALUE));

  if (p_cmpl_cb != NULL && p_clcb->operation != 0) {
    if (p_clcb->operation == GATTC_OPTYPE_READ) {
      cb_data.att_value.handle = p_clcb->s_handle;
      cb_data.att_value.len = p_clcb->counter;

      if (p_data && p_clcb->counter)
        memcpy(cb_data.att_value.value, p_data, cb_data.att_value.len);
    }

    if (p_clcb->operation == GATTC_OPTYPE_WRITE) {
      memset(&cb_data.att_value, 0, sizeof(tGATT_VALUE));
      cb_data.handle = cb_data.att_value.handle = p_clcb->s_handle;
      if (p_clcb->op_subtype == GATT_WRITE_PREPARE) {
        if (p_data) {
          cb_data.att_value = *((tGATT_VALUE*)p_data);
        } else {
          VLOG(1) << "Rcv Prepare write rsp but no data";
        }
      }
    }

    if (p_clcb->operation == GATTC_OPTYPE_CONFIG)
      cb_data.mtu = p_clcb->p_tcb->payload_size;

    if (p_clcb->operation == GATTC_OPTYPE_DISCOVERY) {
      disc_type = p_clcb->op_subtype;
    }
  }

  osi_free_and_reset((void**)&p_clcb->p_attr_buf);

  operation = p_clcb->operation;
  conn_id = p_clcb->conn_id;
  alarm_cancel(p_clcb->gatt_rsp_timer_ent);

  gatt_clcb_dealloc(p_clcb);

  if (p_disc_cmpl_cb && (op == GATTC_OPTYPE_DISCOVERY))
    (*p_disc_cmpl_cb)(conn_id, disc_type, status);
  else if (p_cmpl_cb && op)
    (*p_cmpl_cb)(conn_id, op, status, &cb_data);
  else
    LOG(WARNING) << __func__
                 << StringPrintf(
                        ": not sent out op=%d p_disc_cmpl_cb:%p p_cmpl_cb:%p",
                        operation, p_disc_cmpl_cb, p_cmpl_cb);
}

/** This function cleans up the control blocks when L2CAP channel disconnect */
void gatt_cleanup_upon_disc(const RawAddress& bda, uint16_t reason,
                            tBT_TRANSPORT transport) {
  VLOG(1) << __func__;

  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bda, transport);
  if (!p_tcb) return;

  gatt_set_ch_state(p_tcb, GATT_CH_CLOSE);
  for (uint8_t i = 0; i < GATT_CL_MAX_LCB; i++) {
    tGATT_CLCB* p_clcb = &gatt_cb.clcb[i];
    if (!p_clcb->in_use || p_clcb->p_tcb != p_tcb) continue;

    alarm_cancel(p_clcb->gatt_rsp_timer_ent);
    VLOG(1) << "found p_clcb conn_id=" << +p_clcb->conn_id;
    if (p_clcb->operation == GATTC_OPTYPE_NONE) {
      gatt_clcb_dealloc(p_clcb);
      continue;
    }

    gatt_end_operation(p_clcb, GATT_ERROR, NULL);
  }

  alarm_free(p_tcb->ind_ack_timer);
  p_tcb->ind_ack_timer = NULL;
  alarm_free(p_tcb->conf_timer);
  p_tcb->conf_timer = NULL;
  gatt_free_pending_ind(p_tcb);
  fixed_queue_free(p_tcb->sr_cmd.multi_rsp_q, NULL);
  p_tcb->sr_cmd.multi_rsp_q = NULL;

  for (uint8_t i = 0; i < GATT_MAX_APPS; i++) {
    tGATT_REG* p_reg = &gatt_cb.cl_rcb[i];
    if (p_reg->in_use && p_reg->app_cb.p_conn_cb) {
      uint16_t conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, p_reg->gatt_if);
      VLOG(1) << StringPrintf("found p_reg tcb_idx=%d gatt_if=%d  conn_id=0x%x",
                              p_tcb->tcb_idx, p_reg->gatt_if, conn_id);
      (*p_reg->app_cb.p_conn_cb)(p_reg->gatt_if, bda, conn_id, false, reason,
                                 transport);
    }
  }

  *p_tcb = tGATT_TCB();
  VLOG(1) << __func__ << ": exit";
}
/*******************************************************************************
 *
 * Function         gatt_dbg_req_op_name
 *
 * Description      Get op code description name, for debug information.
 *
 * Returns          uint8_t *: name of the operation.
 *
 ******************************************************************************/
uint8_t* gatt_dbg_op_name(uint8_t op_code) {
  uint8_t pseduo_op_code_idx = op_code & (~GATT_WRITE_CMD_MASK);

  if (op_code == GATT_CMD_WRITE) {
    pseduo_op_code_idx = 0x14; /* just an index to op_code_name */
  }

  if (op_code == GATT_SIGN_CMD_WRITE) {
    pseduo_op_code_idx = 0x15; /* just an index to op_code_name */
  }

  if (pseduo_op_code_idx <= GATT_OP_CODE_MAX)
    return (uint8_t*)op_code_name[pseduo_op_code_idx];
  else
    return (uint8_t*)"Op Code Exceed Max";
}

/*******************************************************************************
 *
 * Function         gatt_dbg_display_uuid
 *
 * Description      Disaplay the UUID
 *
 * Returns          None
 *
 ******************************************************************************/
void gatt_dbg_display_uuid(tBT_UUID bt_uuid) {
  char str_buf[50];

  if (bt_uuid.len == LEN_UUID_16) {
    snprintf(str_buf, sizeof(str_buf), "0x%04x", bt_uuid.uu.uuid16);
  } else if (bt_uuid.len == LEN_UUID_32) {
    snprintf(str_buf, sizeof(str_buf), "0x%08x",
             (unsigned int)bt_uuid.uu.uuid32);
  } else if (bt_uuid.len == LEN_UUID_128) {
    int x = snprintf(
        str_buf, sizeof(str_buf), "0x%02x%02x%02x%02x%02x%02x%02x%02x",
        bt_uuid.uu.uuid128[15], bt_uuid.uu.uuid128[14], bt_uuid.uu.uuid128[13],
        bt_uuid.uu.uuid128[12], bt_uuid.uu.uuid128[11], bt_uuid.uu.uuid128[10],
        bt_uuid.uu.uuid128[9], bt_uuid.uu.uuid128[8]);
    snprintf(
        &str_buf[x], sizeof(str_buf) - x, "%02x%02x%02x%02x%02x%02x%02x%02x",
        bt_uuid.uu.uuid128[7], bt_uuid.uu.uuid128[6], bt_uuid.uu.uuid128[5],
        bt_uuid.uu.uuid128[4], bt_uuid.uu.uuid128[3], bt_uuid.uu.uuid128[2],
        bt_uuid.uu.uuid128[1], bt_uuid.uu.uuid128[0]);
  } else
    strlcpy(str_buf, "Unknown UUID 0", sizeof(str_buf));

  VLOG(1) << StringPrintf("UUID=[%s]", str_buf);
}

/** Returns true if this is one of the background devices for the application,
 * false otherwise */
bool gatt_is_bg_dev_for_app(tGATT_BG_CONN_DEV* p_dev, tGATT_IF gatt_if) {
  return p_dev->gatt_if.count(gatt_if);
}

/** background connection device from the list. Returns pointer to the device
 * record, or nullptr if not found */
tGATT_BG_CONN_DEV* gatt_find_bg_dev(const RawAddress& remote_bda) {
  for (tGATT_BG_CONN_DEV& dev : gatt_cb.bgconn_dev) {
    if (dev.remote_bda == remote_bda) {
      return &dev;
    }
  }
  return nullptr;
}

std::list<tGATT_BG_CONN_DEV>::iterator gatt_find_bg_dev_it(
    const RawAddress& remote_bda) {
  auto& list = gatt_cb.bgconn_dev;
  for (auto it = list.begin(); it != list.end(); it++) {
    if (it->remote_bda == remote_bda) {
      return it;
    }
  }
  return list.end();
}

/** Add a device from the background connection list.  Returns true if device
 * added to the list, or already in list, false otherwise */
bool gatt_add_bg_dev_list(tGATT_REG* p_reg, const RawAddress& bd_addr) {
  tGATT_IF gatt_if = p_reg->gatt_if;

  tGATT_BG_CONN_DEV* p_dev = gatt_find_bg_dev(bd_addr);
  if (p_dev) {
    // device already in the whitelist, just add interested app to the list
    if (!p_dev->gatt_if.insert(gatt_if).second) {
      LOG(ERROR) << "device already in iniator white list";
    }

    return true;
  }
  // the device is not in the whitelist

  if (!BTM_BleUpdateBgConnDev(true, bd_addr)) return false;

  gatt_cb.bgconn_dev.emplace_back();
  tGATT_BG_CONN_DEV& dev = gatt_cb.bgconn_dev.back();
  dev.remote_bda = bd_addr;
  dev.gatt_if.insert(gatt_if);
  return true;
}

/** Remove the application interface for the specified background device */
bool gatt_remove_bg_dev_for_app(tGATT_IF gatt_if, const RawAddress& bd_addr) {
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, BT_TRANSPORT_LE);
  bool status;

  if (p_tcb) gatt_update_app_use_link_flag(gatt_if, p_tcb, false, false);
  status = gatt_update_auto_connect_dev(gatt_if, false, bd_addr);
  return status;
}

/** Removes all registrations for background connection for given device.
 * Returns true if anything was removed, false otherwise */
uint8_t gatt_clear_bg_dev_for_addr(const RawAddress& bd_addr) {
  auto dev_it = gatt_find_bg_dev_it(bd_addr);
  if (dev_it == gatt_cb.bgconn_dev.end()) return false;

  CHECK(BTM_BleUpdateBgConnDev(false, dev_it->remote_bda));
  gatt_cb.bgconn_dev.erase(dev_it);
  return true;
}

/** Remove device from the background connection device list or listening to
 * advertising list.  Returns true if device was on the list and was succesfully
 * removed */
bool gatt_remove_bg_dev_from_list(tGATT_REG* p_reg, const RawAddress& bd_addr) {
  tGATT_IF gatt_if = p_reg->gatt_if;
  auto dev_it = gatt_find_bg_dev_it(bd_addr);
  if (dev_it == gatt_cb.bgconn_dev.end()) return false;

  if (!dev_it->gatt_if.erase(gatt_if)) return false;

  if (!dev_it->gatt_if.empty()) return true;

  // no more apps interested - remove from whitelist and delete record
  CHECK(BTM_BleUpdateBgConnDev(false, dev_it->remote_bda));
  gatt_cb.bgconn_dev.erase(dev_it);
  return true;
}
/** deregister all related back ground connetion device. */
void gatt_deregister_bgdev_list(tGATT_IF gatt_if) {
  auto it = gatt_cb.bgconn_dev.begin();
  auto end = gatt_cb.bgconn_dev.end();
  /* update the BG conn device list */
  while (it != end) {
    it->gatt_if.erase(gatt_if);
    if (it->gatt_if.size()) {
      it++;
      continue;
    }

    BTM_BleUpdateBgConnDev(false, it->remote_bda);
    it = gatt_cb.bgconn_dev.erase(it);
  }
}

/*******************************************************************************
 *
 * Function         gatt_reset_bgdev_list
 *
 * Description      reset bg device list
 *
 * Returns          pointer to the device record
 *
 ******************************************************************************/
void gatt_reset_bgdev_list(void) { gatt_cb.bgconn_dev.clear(); }
/*******************************************************************************
 *
 * Function         gatt_update_auto_connect_dev
 *
 * Description      This function add or remove a device for background
 *                  connection procedure.
 *
 * Parameters       gatt_if: Application ID.
 *                  add: add peer device
 *                  bd_addr: peer device address.
 *
 * Returns          true if connection started; false otherwise.
 *
 ******************************************************************************/
bool gatt_update_auto_connect_dev(tGATT_IF gatt_if, bool add,
                                  const RawAddress& bd_addr) {
  bool ret = false;
  tGATT_REG* p_reg;
  tGATT_TCB* p_tcb = gatt_find_tcb_by_addr(bd_addr, BT_TRANSPORT_LE);

  VLOG(1) << __func__;
  /* Make sure app is registered */
  p_reg = gatt_get_regcb(gatt_if);
  if (p_reg == NULL) {
    LOG(ERROR) << __func__ << " gatt_if is not registered " << +gatt_if;
    return false;
  }

  if (add) {
    ret = gatt_add_bg_dev_list(p_reg, bd_addr);

    if (ret && p_tcb != NULL) {
      /* if a connected device, update the link holding number */
      gatt_update_app_use_link_flag(gatt_if, p_tcb, true, true);
    }
  } else {
    ret = gatt_remove_bg_dev_from_list(p_reg, bd_addr);
  }
  return ret;
}
