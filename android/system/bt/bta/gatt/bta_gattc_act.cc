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
 *  This file contains the GATT client action functions for the state
 *  machine.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bta_gattc"

#include <string.h>

#include <base/callback.h>
#include "bt_common.h"
#include "bt_target.h"
#include "bta_closure_api.h"
#include "bta_gattc_int.h"
#include "bta_sys.h"
#include "btif/include/btif_debug_conn.h"
#include "l2c_api.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "stack/l2cap/l2c_int.h"
#include "utl.h"

#if (BTA_HH_LE_INCLUDED == TRUE)
#include "bta_hh_int.h"
#endif

/*****************************************************************************
 *  Constants
 ****************************************************************************/
static void bta_gattc_conn_cback(tGATT_IF gattc_if, const RawAddress& bda,
                                 uint16_t conn_id, bool connected,
                                 tGATT_DISCONN_REASON reason,
                                 tBT_TRANSPORT transport);

static void bta_gattc_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                 tGATT_STATUS status,
                                 tGATT_CL_COMPLETE* p_data);
static void bta_gattc_cmpl_sendmsg(uint16_t conn_id, tGATTC_OPTYPE op,
                                   tBTA_GATT_STATUS status,
                                   tGATT_CL_COMPLETE* p_data);

static void bta_gattc_deregister_cmpl(tBTA_GATTC_RCB* p_clreg);
static void bta_gattc_enc_cmpl_cback(tGATT_IF gattc_if, const RawAddress& bda);
static void bta_gattc_cong_cback(uint16_t conn_id, bool congested);
static void bta_gattc_phy_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                       uint8_t tx_phy, uint8_t rx_phy,
                                       uint8_t status);
static void bta_gattc_conn_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t interval, uint16_t latency,
                                        uint16_t timeout, uint8_t status);

static tGATT_CBACK bta_gattc_cl_cback = {bta_gattc_conn_cback,
                                         bta_gattc_cmpl_cback,
                                         bta_gattc_disc_res_cback,
                                         bta_gattc_disc_cmpl_cback,
                                         NULL,
                                         bta_gattc_enc_cmpl_cback,
                                         bta_gattc_cong_cback,
                                         bta_gattc_phy_update_cback,
                                         bta_gattc_conn_update_cback};

/* opcode(tGATTC_OPTYPE) order has to be comply with internal event order */
static uint16_t bta_gattc_opcode_to_int_evt[] = {
    BTA_GATTC_API_READ_EVT, BTA_GATTC_API_WRITE_EVT, BTA_GATTC_API_EXEC_EVT,
    BTA_GATTC_API_CFG_MTU_EVT};

static const char* bta_gattc_op_code_name[] = {
    "Unknown", "Discovery", "Read",         "Write",
    "Exec",    "Config",    "Notification", "Indication"};
/*****************************************************************************
 *  Action Functions
 ****************************************************************************/

void bta_gattc_reset_discover_st(tBTA_GATTC_SERV* p_srcb,
                                 tBTA_GATT_STATUS status);

/*******************************************************************************
 *
 * Function         bta_gattc_enable
 *
 * Description      Enables GATTC module
 *
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_enable() {
  APPL_TRACE_DEBUG("%s", __func__);

  if (bta_gattc_cb.state == BTA_GATTC_STATE_DISABLED) {
    /* initialize control block */
    memset(&bta_gattc_cb, 0, sizeof(tBTA_GATTC_CB));
    bta_gattc_cb.state = BTA_GATTC_STATE_ENABLED;
  } else {
    APPL_TRACE_DEBUG("GATTC is already enabled");
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_disable
 *
 * Description      Disable GATTC module by cleaning up all active connections
 *                  and deregister all application.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_disable() {
  uint8_t i;

  APPL_TRACE_DEBUG("%s", __func__);

  if (bta_gattc_cb.state != BTA_GATTC_STATE_ENABLED) {
    APPL_TRACE_ERROR("not enabled, or disabled in progress");
    return;
  }

  for (i = 0; i < BTA_GATTC_CL_MAX; i++) {
    if (bta_gattc_cb.cl_rcb[i].in_use) {
      bta_gattc_cb.state = BTA_GATTC_STATE_DISABLING;
/* don't deregister HH GATT IF */
/* HH GATT IF will be deregistered by bta_hh_le_deregister when disable HH */
#if (BTA_HH_LE_INCLUDED == TRUE)
      if (!bta_hh_le_is_hh_gatt_if(bta_gattc_cb.cl_rcb[i].client_if)) {
#endif
        bta_gattc_deregister(&bta_gattc_cb.cl_rcb[i]);
#if (BTA_HH_LE_INCLUDED == TRUE)
      }
#endif
    }
  }

  /* no registered apps, indicate disable completed */
  if (bta_gattc_cb.state != BTA_GATTC_STATE_DISABLING) {
    memset(&bta_gattc_cb, 0, sizeof(tBTA_GATTC_CB));
    bta_gattc_cb.state = BTA_GATTC_STATE_DISABLED;
  }
}

/** start an application interface */
void bta_gattc_start_if(uint8_t client_if) {
  if (!bta_gattc_cl_get_regcb(client_if)) {
    APPL_TRACE_ERROR("Unable to start app.: Unknown interface =%d", client_if);
    return;
  }

  GATT_StartIf(client_if);
}

/** Register a GATT client application with BTA */
void bta_gattc_register(tBT_UUID* p_app_uuid, tBTA_GATTC_CBACK* p_cback,
                        BtaAppRegisterCallback cb) {
  tBTA_GATT_STATUS status = BTA_GATT_NO_RESOURCES;
  uint8_t client_if = 0;
  APPL_TRACE_DEBUG("%s: state %d", __func__, bta_gattc_cb.state);

  /* check if  GATTC module is already enabled . Else enable */
  if (bta_gattc_cb.state == BTA_GATTC_STATE_DISABLED) {
    bta_gattc_enable();
  }
  /* todo need to check duplicate uuid */
  for (uint8_t i = 0; i < BTA_GATTC_CL_MAX; i++) {
    if (!bta_gattc_cb.cl_rcb[i].in_use) {
      if ((p_app_uuid == NULL) ||
          (bta_gattc_cb.cl_rcb[i].client_if =
               GATT_Register(p_app_uuid, &bta_gattc_cl_cback)) == 0) {
        APPL_TRACE_ERROR("Register with GATT stack failed.");
        status = BTA_GATT_ERROR;
      } else {
        bta_gattc_cb.cl_rcb[i].in_use = true;
        bta_gattc_cb.cl_rcb[i].p_cback = p_cback;
        memcpy(&bta_gattc_cb.cl_rcb[i].app_uuid, p_app_uuid, sizeof(tBT_UUID));

        /* BTA use the same client interface as BTE GATT statck */
        client_if = bta_gattc_cb.cl_rcb[i].client_if;

        do_in_bta_thread(FROM_HERE, base::Bind(&bta_gattc_start_if, client_if));

        status = BTA_GATT_OK;
        break;
      }
    }
  }

  if (!cb.is_null()) cb.Run(client_if, status);
}

/*******************************************************************************
 *
 * Function         bta_gattc_deregister
 *
 * Description      De-Register a GATT client application with BTA.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_deregister(tBTA_GATTC_RCB* p_clreg) {
  uint8_t i;
  BT_HDR buf;

  if (p_clreg != NULL) {
    /* remove bg connection associated with this rcb */
    for (i = 0; i < BTA_GATTC_KNOWN_SR_MAX; i++) {
      if (bta_gattc_cb.bg_track[i].in_use) {
        if (bta_gattc_cb.bg_track[i].cif_mask &
            (1 << (p_clreg->client_if - 1))) {
          bta_gattc_mark_bg_conn(p_clreg->client_if,
                                 bta_gattc_cb.bg_track[i].remote_bda, false);
          GATT_CancelConnect(p_clreg->client_if,
                             bta_gattc_cb.bg_track[i].remote_bda, false);
        }
      }
    }

    if (p_clreg->num_clcb > 0) {
      /* close all CLCB related to this app */
      for (i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
        if (bta_gattc_cb.clcb[i].in_use &&
            (bta_gattc_cb.clcb[i].p_rcb == p_clreg)) {
          p_clreg->dereg_pending = true;

          buf.event = BTA_GATTC_API_CLOSE_EVT;
          buf.layer_specific = bta_gattc_cb.clcb[i].bta_conn_id;
          bta_gattc_close(&bta_gattc_cb.clcb[i], (tBTA_GATTC_DATA*)&buf);
        }
      }
    } else
      bta_gattc_deregister_cmpl(p_clreg);
  } else {
    APPL_TRACE_ERROR("%s: Deregister Failed unknown client cif", __func__);
    bta_hh_cleanup_disable(BTA_HH_OK);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_process_api_open
 *
 * Description      process connect API request.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_process_api_open(tBTA_GATTC_DATA* p_msg) {
  uint16_t event = ((BT_HDR*)p_msg)->event;
  tBTA_GATTC_CLCB* p_clcb = NULL;
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(p_msg->api_conn.client_if);

  if (p_clreg != NULL) {
    if (p_msg->api_conn.is_direct) {
      p_clcb = bta_gattc_find_alloc_clcb(p_msg->api_conn.client_if,
                                         p_msg->api_conn.remote_bda,
                                         p_msg->api_conn.transport);
      if (p_clcb != NULL) {
        bta_gattc_sm_execute(p_clcb, event, p_msg);
      } else {
        APPL_TRACE_ERROR("No resources to open a new connection.");

        bta_gattc_send_open_cback(
            p_clreg, BTA_GATT_NO_RESOURCES, p_msg->api_conn.remote_bda,
            BTA_GATT_INVALID_CONN_ID, p_msg->api_conn.transport, 0);
      }
    } else {
      bta_gattc_init_bk_conn(&p_msg->api_conn, p_clreg);
    }
  } else {
    APPL_TRACE_ERROR("%s: Failed, unknown client_if: %d", __func__,
                     p_msg->api_conn.client_if);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_process_api_open_cancel
 *
 * Description      process connect API request.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_process_api_open_cancel(tBTA_GATTC_DATA* p_msg) {
  uint16_t event = ((BT_HDR*)p_msg)->event;
  tBTA_GATTC_CLCB* p_clcb = NULL;
  tBTA_GATTC_RCB* p_clreg;
  tBTA_GATTC cb_data;

  if (p_msg->api_cancel_conn.is_direct) {
    p_clcb = bta_gattc_find_clcb_by_cif(p_msg->api_cancel_conn.client_if,
                                        p_msg->api_cancel_conn.remote_bda,
                                        BTA_GATT_TRANSPORT_LE);
    if (p_clcb != NULL) {
      bta_gattc_sm_execute(p_clcb, event, p_msg);
    } else {
      APPL_TRACE_ERROR("No such connection need to be cancelled");

      p_clreg = bta_gattc_cl_get_regcb(p_msg->api_cancel_conn.client_if);

      if (p_clreg && p_clreg->p_cback) {
        cb_data.status = BTA_GATT_ERROR;
        (*p_clreg->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
      }
    }
  } else {
    bta_gattc_cancel_bk_conn(&p_msg->api_cancel_conn);
  }
}

/** process encryption complete message */
void bta_gattc_process_enc_cmpl(tGATT_IF client_if, const RawAddress& bda) {
  tBTA_GATTC_RCB* p_clreg;
  tBTA_GATTC cb_data;

  p_clreg = bta_gattc_cl_get_regcb(client_if);

  if (p_clreg && p_clreg->p_cback) {
    memset(&cb_data, 0, sizeof(tBTA_GATTC));

    cb_data.enc_cmpl.client_if = client_if;
    cb_data.enc_cmpl.remote_bda = bda;

    (*p_clreg->p_cback)(BTA_GATTC_ENC_CMPL_CB_EVT, &cb_data);
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_cancel_open_error
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_cancel_open_error(tBTA_GATTC_CLCB* p_clcb,
                                 UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  cb_data.status = BTA_GATT_ERROR;

  if (p_clcb && p_clcb->p_rcb && p_clcb->p_rcb->p_cback)
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
}

/*******************************************************************************
 *
 * Function         bta_gattc_open_error
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_open_error(tBTA_GATTC_CLCB* p_clcb,
                          UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  APPL_TRACE_ERROR("Connection already opened. wrong state");

  bta_gattc_send_open_cback(p_clcb->p_rcb, BTA_GATT_OK, p_clcb->bda,
                            p_clcb->bta_conn_id, p_clcb->transport, 0);
}
/*******************************************************************************
 *
 * Function         bta_gattc_open_fail
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_open_fail(tBTA_GATTC_CLCB* p_clcb,
                         UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  bta_gattc_send_open_cback(p_clcb->p_rcb, BTA_GATT_ERROR, p_clcb->bda,
                            p_clcb->bta_conn_id, p_clcb->transport, 0);
  /* open failure, remove clcb */
  bta_gattc_clcb_dealloc(p_clcb);
}

/*******************************************************************************
 *
 * Function         bta_gattc_open
 *
 * Description      Process API connection function.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_open(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_DATA gattc_data;

  /* open/hold a connection */
  if (!GATT_Connect(p_clcb->p_rcb->client_if, p_data->api_conn.remote_bda, true,
                    p_data->api_conn.transport, p_data->api_conn.opportunistic,
                    p_data->api_conn.initiating_phys)) {
    APPL_TRACE_ERROR("Connection open failure");

    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_OPEN_FAIL_EVT, p_data);
  } else {
    /* a connected remote device */
    if (GATT_GetConnIdIfConnected(
            p_clcb->p_rcb->client_if, p_data->api_conn.remote_bda,
            &p_clcb->bta_conn_id, p_data->api_conn.transport)) {
      gattc_data.int_conn.hdr.layer_specific = p_clcb->bta_conn_id;

      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT, &gattc_data);
    }
    /* else wait for the callback event */
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_init_bk_conn
 *
 * Description      Process API Open for a background connection
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_init_bk_conn(tBTA_GATTC_API_OPEN* p_data,
                            tBTA_GATTC_RCB* p_clreg) {
  tBTA_GATT_STATUS status = BTA_GATT_NO_RESOURCES;
  uint16_t conn_id;
  tBTA_GATTC_CLCB* p_clcb;
  tBTA_GATTC_DATA gattc_data;

  if (bta_gattc_mark_bg_conn(p_data->client_if, p_data->remote_bda, true)) {
    /* always call open to hold a connection */
    if (!GATT_Connect(p_data->client_if, p_data->remote_bda, false,
                      p_data->transport, false)) {
      status = BTA_GATT_ERROR;
      LOG(ERROR) << __func__ << " unable to connect to remote bd_addr:"
                 << p_data->remote_bda;

    } else {
      status = BTA_GATT_OK;

      /* if is a connected remote device */
      if (GATT_GetConnIdIfConnected(p_data->client_if, p_data->remote_bda,
                                    &conn_id, p_data->transport)) {
        p_clcb = bta_gattc_find_alloc_clcb(
            p_data->client_if, p_data->remote_bda, BTA_GATT_TRANSPORT_LE);
        if (p_clcb != NULL) {
          gattc_data.hdr.layer_specific = p_clcb->bta_conn_id = conn_id;

          /* open connection */
          bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT, &gattc_data);
          status = BTA_GATT_OK;
        }
      }
    }
  }

  /* open failure, report OPEN_EVT */
  if (status != BTA_GATT_OK) {
    bta_gattc_send_open_cback(p_clreg, status, p_data->remote_bda,
                              BTA_GATT_INVALID_CONN_ID, BTA_GATT_TRANSPORT_LE,
                              0);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_cancel_bk_conn
 *
 * Description      Process API Cancel Open for a background connection
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_cancel_bk_conn(tBTA_GATTC_API_CANCEL_OPEN* p_data) {
  tBTA_GATTC_RCB* p_clreg;
  tBTA_GATTC cb_data;
  cb_data.status = BTA_GATT_ERROR;

  /* remove the device from the bg connection mask */
  if (bta_gattc_mark_bg_conn(p_data->client_if, p_data->remote_bda, false)) {
    if (GATT_CancelConnect(p_data->client_if, p_data->remote_bda, false)) {
      cb_data.status = BTA_GATT_OK;
    } else {
      APPL_TRACE_ERROR("%s: failed", __func__);
    }
  }
  p_clreg = bta_gattc_cl_get_regcb(p_data->client_if);

  if (p_clreg && p_clreg->p_cback) {
    (*p_clreg->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_int_cancel_open_ok
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_cancel_open_ok(tBTA_GATTC_CLCB* p_clcb,
                              UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (p_clcb->p_rcb->p_cback) {
    cb_data.status = BTA_GATT_OK;
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
  }

  bta_gattc_clcb_dealloc(p_clcb);
}
/*******************************************************************************
 *
 * Function         bta_gattc_cancel_open
 *
 * Description
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_cancel_open(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (GATT_CancelConnect(p_clcb->p_rcb->client_if,
                         p_data->api_cancel_conn.remote_bda, true)) {
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CANCEL_OPEN_OK_EVT, p_data);
  } else {
    if (p_clcb->p_rcb->p_cback) {
      cb_data.status = BTA_GATT_ERROR;
      (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CANCEL_OPEN_EVT, &cb_data);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_conn
 *
 * Description      receive connection callback from stack
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_conn(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_IF gatt_if;
  APPL_TRACE_DEBUG("%s: server cache state=%d", __func__,
                   p_clcb->p_srcb->state);

  if (p_data != NULL) {
    APPL_TRACE_DEBUG("%s: conn_id=%d", __func__, p_data->hdr.layer_specific);
    p_clcb->bta_conn_id = p_data->int_conn.hdr.layer_specific;

    GATT_GetConnectionInfor(p_data->hdr.layer_specific, &gatt_if, p_clcb->bda,
                            &p_clcb->transport);
  }

  p_clcb->p_srcb->connected = true;

  if (p_clcb->p_srcb->mtu == 0) p_clcb->p_srcb->mtu = GATT_DEF_BLE_MTU_SIZE;

  /* start database cache if needed */
  if (p_clcb->p_srcb->p_srvc_cache == NULL ||
      p_clcb->p_srcb->state != BTA_GATTC_SERV_IDLE) {
    if (p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE) {
      p_clcb->p_srcb->state = BTA_GATTC_SERV_LOAD;
      if (bta_gattc_cache_load(p_clcb)) {
        p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
        bta_gattc_reset_discover_st(p_clcb->p_srcb, BTA_GATT_OK);
      } else {
        p_clcb->p_srcb->state = BTA_GATTC_SERV_DISC;
        /* cache load failure, start discovery */
        bta_gattc_start_discover(p_clcb, NULL);
      }
    } else /* cache is building */
      p_clcb->state = BTA_GATTC_DISCOVER_ST;
  }

  else {
    /* a pending service handle change indication */
    if (p_clcb->p_srcb->srvc_hdl_chg) {
      p_clcb->p_srcb->srvc_hdl_chg = false;
      /* start discovery */
      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    }
  }

  if (p_clcb->p_rcb) {
    /* there is no RM for GATT */
    if (p_clcb->transport == BTA_TRANSPORT_BR_EDR)
      bta_sys_conn_open(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);

    bta_gattc_send_open_cback(p_clcb->p_rcb, BTA_GATT_OK, p_clcb->bda,
                              p_clcb->bta_conn_id, p_clcb->transport,
                              p_clcb->p_srcb->mtu);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_close_fail
 *
 * Description      close a  connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_close_fail(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC cb_data;

  if (p_clcb->p_rcb->p_cback) {
    memset(&cb_data, 0, sizeof(tBTA_GATTC));
    cb_data.close.client_if = p_clcb->p_rcb->client_if;
    cb_data.close.conn_id = p_data->hdr.layer_specific;
    cb_data.close.remote_bda = p_clcb->bda;
    cb_data.close.status = BTA_GATT_ERROR;
    cb_data.close.reason = BTA_GATT_CONN_NONE;

    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CLOSE_EVT, &cb_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_api_close
 *
 * Description      close a GATTC connection.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_close(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_CBACK* p_cback = p_clcb->p_rcb->p_cback;
  tBTA_GATTC_RCB* p_clreg = p_clcb->p_rcb;
  tBTA_GATTC cb_data;

  APPL_TRACE_DEBUG("%s: conn_id=%d", __func__, p_clcb->bta_conn_id);

  cb_data.close.client_if = p_clcb->p_rcb->client_if;
  cb_data.close.conn_id = p_clcb->bta_conn_id;
  cb_data.close.reason = p_clcb->reason;
  cb_data.close.status = p_clcb->status;
  cb_data.close.remote_bda = p_clcb->bda;

  if (p_clcb->transport == BTA_TRANSPORT_BR_EDR)
    bta_sys_conn_close(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);

  bta_gattc_clcb_dealloc(p_clcb);

  if (p_data->hdr.event == BTA_GATTC_API_CLOSE_EVT) {
    cb_data.close.status = GATT_Disconnect(p_data->hdr.layer_specific);
  } else if (p_data->hdr.event == BTA_GATTC_INT_DISCONN_EVT) {
    cb_data.close.status = p_data->int_conn.reason;
    cb_data.close.reason = p_data->int_conn.reason;
  }

  if (p_cback) (*p_cback)(BTA_GATTC_CLOSE_EVT, &cb_data);

  if (p_clreg->num_clcb == 0 && p_clreg->dereg_pending) {
    bta_gattc_deregister_cmpl(p_clreg);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_reset_discover_st
 *
 * Description      when a SRCB finished discovery, tell all related clcb.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_reset_discover_st(tBTA_GATTC_SERV* p_srcb,
                                 tBTA_GATT_STATUS status) {
  uint8_t i;

  for (i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
    if (bta_gattc_cb.clcb[i].p_srcb == p_srcb) {
      bta_gattc_cb.clcb[i].status = status;
      bta_gattc_sm_execute(&bta_gattc_cb.clcb[i], BTA_GATTC_DISCOVER_CMPL_EVT,
                           NULL);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_disc_close
 *
 * Description      close a GATTC connection while in discovery state.
 *
 * Returns          void
 *
 ******************************************************************************/
void bta_gattc_disc_close(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  APPL_TRACE_DEBUG("%s: Discovery cancel conn_id=%d", __func__,
                   p_clcb->bta_conn_id);

  if (p_clcb->disc_active)
    bta_gattc_reset_discover_st(p_clcb->p_srcb, BTA_GATT_ERROR);
  else
    p_clcb->state = BTA_GATTC_CONN_ST;

  // This function only gets called as the result of a BTA_GATTC_API_CLOSE_EVT
  // while in the BTA_GATTC_DISCOVER_ST state. Once the state changes, the
  // connection itself still needs to be closed to resolve the original event.
  if (p_clcb->state == BTA_GATTC_CONN_ST) {
    APPL_TRACE_DEBUG(
        "State is back to BTA_GATTC_CONN_ST. "
        "Trigger connection close");
    bta_gattc_close(p_clcb, p_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_set_discover_st
 *
 * Description      when a SRCB start discovery, tell all related clcb and set
 *                  the state.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_set_discover_st(tBTA_GATTC_SERV* p_srcb) {
  uint8_t i;

  L2CA_EnableUpdateBleConnParams(p_srcb->server_bda, false);
  for (i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
    if (bta_gattc_cb.clcb[i].p_srcb == p_srcb) {
      bta_gattc_cb.clcb[i].status = BTA_GATT_OK;
      bta_gattc_cb.clcb[i].state = BTA_GATTC_DISCOVER_ST;
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_restart_discover
 *
 * Description      process service change in discovery state, mark up the auto
 *                  update flag and set status to be discovery cancel for
 *                  current discovery.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_restart_discover(tBTA_GATTC_CLCB* p_clcb,
                                UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  p_clcb->status = BTA_GATT_CANCEL;
  p_clcb->auto_update = BTA_GATTC_DISC_WAITING;
}

/*******************************************************************************
 *
 * Function         bta_gattc_cfg_mtu
 *
 * Description      Configure MTU size on the GATT connection.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_cfg_mtu(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATT_STATUS status;

  if (bta_gattc_enqueue(p_clcb, p_data)) {
    status = GATTC_ConfigureMTU(p_clcb->bta_conn_id, p_data->api_mtu.mtu);

    /* if failed, return callback here */
    if (status != GATT_SUCCESS && status != GATT_CMD_STARTED) {
      /* Dequeue the data, if it was enqueued */
      if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

      bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_CONFIG, status,
                             NULL);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_start_discover
 *
 * Description      Start a discovery on server.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_start_discover(tBTA_GATTC_CLCB* p_clcb,
                              UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  APPL_TRACE_DEBUG("%s: conn_id=%d p_clcb->p_srcb->state = %d ", __func__,
                   p_clcb->bta_conn_id, p_clcb->p_srcb->state);

  if (((p_clcb->p_q_cmd == NULL ||
        p_clcb->auto_update == BTA_GATTC_REQ_WAITING) &&
       p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE) ||
      p_clcb->p_srcb->state == BTA_GATTC_SERV_DISC)
  /* no pending operation, start discovery right away */
  {
    p_clcb->auto_update = BTA_GATTC_NO_SCHEDULE;

    if (p_clcb->p_srcb != NULL) {
      /* clear the service change mask */
      p_clcb->p_srcb->srvc_hdl_chg = false;
      p_clcb->p_srcb->update_count = 0;
      p_clcb->p_srcb->state = BTA_GATTC_SERV_DISC_ACT;

      if (p_clcb->transport == BTA_TRANSPORT_LE)
        L2CA_EnableUpdateBleConnParams(p_clcb->p_srcb->server_bda, false);

      /* set all srcb related clcb into discovery ST */
      bta_gattc_set_discover_st(p_clcb->p_srcb);

      p_clcb->status = bta_gattc_init_cache(p_clcb->p_srcb);
      if (p_clcb->status == BTA_GATT_OK) {
        p_clcb->status = bta_gattc_discover_pri_service(
            p_clcb->bta_conn_id, p_clcb->p_srcb, GATT_DISC_SRVC_ALL);
      }
      if (p_clcb->status != BTA_GATT_OK) {
        APPL_TRACE_ERROR("discovery on server failed");
        bta_gattc_reset_discover_st(p_clcb->p_srcb, p_clcb->status);
      } else
        p_clcb->disc_active = true;
    } else {
      APPL_TRACE_ERROR("unknown device, can not start discovery");
    }
  }
  /* pending operation, wait until it finishes */
  else {
    p_clcb->auto_update = BTA_GATTC_DISC_WAITING;

    if (p_clcb->p_srcb->state == BTA_GATTC_SERV_IDLE)
      p_clcb->state = BTA_GATTC_CONN_ST; /* set clcb state */
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_disc_cmpl
 *
 * Description      discovery on server is finished
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_disc_cmpl(tBTA_GATTC_CLCB* p_clcb,
                         UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  tBTA_GATTC_DATA* p_q_cmd = p_clcb->p_q_cmd;

  APPL_TRACE_DEBUG("%s: conn_id=%d", __func__, p_clcb->bta_conn_id);

  if (p_clcb->transport == BTA_TRANSPORT_LE)
    L2CA_EnableUpdateBleConnParams(p_clcb->p_srcb->server_bda, true);
  p_clcb->p_srcb->state = BTA_GATTC_SERV_IDLE;
  p_clcb->disc_active = false;

  if (p_clcb->status != GATT_SUCCESS) {
    /* clean up cache */
    if (p_clcb->p_srcb && p_clcb->p_srcb->p_srvc_cache) {
      list_free(p_clcb->p_srcb->p_srvc_cache);
      p_clcb->p_srcb->p_srvc_cache = NULL;
    }

    /* used to reset cache in application */
    bta_gattc_cache_reset(p_clcb->p_srcb->server_bda);
  }
  if (p_clcb->p_srcb && p_clcb->p_srcb->p_srvc_list) {
    /* release pending attribute list buffer */
    osi_free_and_reset((void**)&p_clcb->p_srcb->p_srvc_list);
  }

  if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING) {
    /* start discovery again */
    p_clcb->auto_update = BTA_GATTC_REQ_WAITING;
    bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
  }
  /* get any queued command to proceed */
  else if (p_q_cmd != NULL) {
    p_clcb->p_q_cmd = NULL;
    /* execute pending operation of link block still present */
    if (l2cu_find_lcb_by_bd_addr(p_clcb->p_srcb->server_bda, BT_TRANSPORT_LE)) {
      bta_gattc_sm_execute(p_clcb, p_q_cmd->hdr.event, p_q_cmd);
    }
    /* if the command executed requeued the cmd, we don't
     * want to free the underlying buffer that's being
     * referenced by p_clcb->p_q_cmd
     */
    if (p_q_cmd != p_clcb->p_q_cmd) osi_free_and_reset((void**)&p_q_cmd);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_read
 *
 * Description      Read an attribute
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_read(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  if (!bta_gattc_enqueue(p_clcb, p_data)) return;

  tBTA_GATT_STATUS status;
  if (p_data->api_read.handle != 0) {
    tGATT_READ_PARAM read_param;
    memset(&read_param, 0, sizeof(tGATT_READ_PARAM));
    read_param.by_handle.handle = p_data->api_read.handle;
    read_param.by_handle.auth_req = p_data->api_read.auth_req;
    status = GATTC_Read(p_clcb->bta_conn_id, GATT_READ_BY_HANDLE, &read_param);
  } else {
    tGATT_READ_PARAM read_param;
    memset(&read_param, 0, sizeof(tGATT_READ_BY_TYPE));

    read_param.char_type.s_handle = p_data->api_read.s_handle;
    read_param.char_type.e_handle = p_data->api_read.e_handle;
    read_param.char_type.uuid = p_data->api_read.uuid;
    read_param.char_type.auth_req = p_data->api_read.auth_req;
    status = GATTC_Read(p_clcb->bta_conn_id, GATT_READ_BY_TYPE, &read_param);
  }

  /* read fail */
  if (status != BTA_GATT_OK) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_READ, status,
                           NULL);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_read_multi
 *
 * Description      read multiple
 *
 * Returns          None.
 ******************************************************************************/
void bta_gattc_read_multi(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATT_STATUS status = BTA_GATT_OK;
  tGATT_READ_PARAM read_param;

  if (bta_gattc_enqueue(p_clcb, p_data)) {
    memset(&read_param, 0, sizeof(tGATT_READ_PARAM));

    if (status == BTA_GATT_OK) {
      read_param.read_multiple.num_handles = p_data->api_read_multi.num_attr;
      read_param.read_multiple.auth_req = p_data->api_read_multi.auth_req;
      memcpy(&read_param.read_multiple.handles, p_data->api_read_multi.handles,
             sizeof(uint16_t) * p_data->api_read_multi.num_attr);

      status = GATTC_Read(p_clcb->bta_conn_id, GATT_READ_MULTIPLE, &read_param);
    }

    /* read fail */
    if (status != BTA_GATT_OK) {
      /* Dequeue the data, if it was enqueued */
      if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

      bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_READ, status,
                             NULL);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_write
 *
 * Description      Write an attribute
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_write(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  if (!bta_gattc_enqueue(p_clcb, p_data)) return;

  tBTA_GATT_STATUS status = BTA_GATT_OK;
  tGATT_VALUE attr;

  attr.conn_id = p_clcb->bta_conn_id;
  attr.handle = p_data->api_write.handle;
  attr.offset = p_data->api_write.offset;
  attr.len = p_data->api_write.len;
  attr.auth_req = p_data->api_write.auth_req;

  if (p_data->api_write.p_value)
    memcpy(attr.value, p_data->api_write.p_value, p_data->api_write.len);

  status =
      GATTC_Write(p_clcb->bta_conn_id, p_data->api_write.write_type, &attr);

  /* write fail */
  if (status != BTA_GATT_OK) {
    /* Dequeue the data, if it was enqueued */
    if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

    bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_WRITE, status,
                           NULL);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_execute
 *
 * Description      send execute write
 *
 * Returns          None.
 ******************************************************************************/
void bta_gattc_execute(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATT_STATUS status;

  if (bta_gattc_enqueue(p_clcb, p_data)) {
    status =
        GATTC_ExecuteWrite(p_clcb->bta_conn_id, p_data->api_exec.is_execute);

    if (status != BTA_GATT_OK) {
      /* Dequeue the data, if it was enqueued */
      if (p_clcb->p_q_cmd == p_data) p_clcb->p_q_cmd = NULL;

      bta_gattc_cmpl_sendmsg(p_clcb->bta_conn_id, GATTC_OPTYPE_EXE_WRITE,
                             status, NULL);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_confirm
 *
 * Description      send handle value confirmation
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_confirm(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  uint16_t handle = p_data->api_confirm.handle;

  if (GATTC_SendHandleValueConfirm(p_data->api_confirm.hdr.layer_specific,
                                   handle) != GATT_SUCCESS) {
    APPL_TRACE_ERROR("%s: to handle [0x%04x] failed", __func__, handle);
  } else {
    /* if over BR_EDR, inform PM for mode change */
    if (p_clcb->transport == BTA_TRANSPORT_BR_EDR) {
      bta_sys_busy(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
      bta_sys_idle(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_read_cmpl
 *
 * Description      read complete
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_read_cmpl(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_OP_CMPL* p_data) {
  GATT_READ_OP_CB cb = p_clcb->p_q_cmd->api_read.read_cb;
  void* my_cb_data = p_clcb->p_q_cmd->api_read.read_cb_data;

  /* if it was read by handle, return the handle requested, if read by UUID, use
   * handle returned from remote
   */
  uint16_t handle = p_clcb->p_q_cmd->api_read.handle;
  if (handle == 0) handle = p_data->p_cmpl->att_value.handle;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);

  if (cb) {
    cb(p_clcb->bta_conn_id, p_data->status, handle,
       p_data->p_cmpl->att_value.len, p_data->p_cmpl->att_value.value,
       my_cb_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_write_cmpl
 *
 * Description      write complete
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_write_cmpl(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_OP_CMPL* p_data) {
  GATT_WRITE_OP_CB cb = p_clcb->p_q_cmd->api_write.write_cb;
  void* my_cb_data = p_clcb->p_q_cmd->api_write.write_cb_data;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);

  if (cb) {
    cb(p_clcb->bta_conn_id, p_data->status, p_data->p_cmpl->att_value.handle,
       my_cb_data);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_exec_cmpl
 *
 * Description      execute write complete
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_exec_cmpl(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_OP_CMPL* p_data) {
  tBTA_GATTC cb_data;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);
  p_clcb->status = BTA_GATT_OK;

  /* execute complete, callback */
  cb_data.exec_cmpl.conn_id = p_clcb->bta_conn_id;
  cb_data.exec_cmpl.status = p_data->status;

  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_EXEC_EVT, &cb_data);
}

/*******************************************************************************
 *
 * Function         bta_gattc_cfg_mtu_cmpl
 *
 * Description      configure MTU operation complete
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_cfg_mtu_cmpl(tBTA_GATTC_CLCB* p_clcb,
                            tBTA_GATTC_OP_CMPL* p_data) {
  tBTA_GATTC cb_data;

  osi_free_and_reset((void**)&p_clcb->p_q_cmd);

  if (p_data->p_cmpl && p_data->status == BTA_GATT_OK)
    p_clcb->p_srcb->mtu = p_data->p_cmpl->mtu;

  /* configure MTU complete, callback */
  p_clcb->status = p_data->status;
  cb_data.cfg_mtu.conn_id = p_clcb->bta_conn_id;
  cb_data.cfg_mtu.status = p_data->status;
  cb_data.cfg_mtu.mtu = p_clcb->p_srcb->mtu;

  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CFG_MTU_EVT, &cb_data);
}
/*******************************************************************************
 *
 * Function         bta_gattc_op_cmpl
 *
 * Description      operation completed.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_op_cmpl(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  uint8_t op = (uint8_t)p_data->op_cmpl.op_code;
  uint8_t mapped_op = 0;

  APPL_TRACE_DEBUG("%s: op = %d", __func__, op);

  if (op == GATTC_OPTYPE_INDICATION || op == GATTC_OPTYPE_NOTIFICATION) {
    APPL_TRACE_ERROR("unexpected operation, ignored");
  } else if (op >= GATTC_OPTYPE_READ) {
    if (p_clcb->p_q_cmd == NULL) {
      APPL_TRACE_ERROR("No pending command");
      return;
    }
    if (p_clcb->p_q_cmd->hdr.event !=
        bta_gattc_opcode_to_int_evt[op - GATTC_OPTYPE_READ]) {
      mapped_op = p_clcb->p_q_cmd->hdr.event - BTA_GATTC_API_READ_EVT +
                  GATTC_OPTYPE_READ;
      if (mapped_op > GATTC_OPTYPE_INDICATION) mapped_op = 0;

      APPL_TRACE_ERROR(
          "expect op:(%s :0x%04x), receive unexpected operation (%s).",
          bta_gattc_op_code_name[mapped_op], p_clcb->p_q_cmd->hdr.event,
          bta_gattc_op_code_name[op]);
      return;
    }

    /* Except for MTU configuration, discard responses if service change
     * indication is received before operation completed
     */
    if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING &&
        p_clcb->p_srcb->srvc_hdl_chg && op != GATTC_OPTYPE_CONFIG) {
      APPL_TRACE_DEBUG(
          "Discard all responses when service change indication is received.");
      p_data->op_cmpl.status = GATT_ERROR;
    }

    /* service handle change void the response, discard it */
    if (op == GATTC_OPTYPE_READ)
      bta_gattc_read_cmpl(p_clcb, &p_data->op_cmpl);

    else if (op == GATTC_OPTYPE_WRITE)
      bta_gattc_write_cmpl(p_clcb, &p_data->op_cmpl);

    else if (op == GATTC_OPTYPE_EXE_WRITE)
      bta_gattc_exec_cmpl(p_clcb, &p_data->op_cmpl);

    else if (op == GATTC_OPTYPE_CONFIG)
      bta_gattc_cfg_mtu_cmpl(p_clcb, &p_data->op_cmpl);

    if (p_clcb->auto_update == BTA_GATTC_DISC_WAITING) {
      p_clcb->auto_update = BTA_GATTC_REQ_WAITING;
      bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_op_cmpl
 *
 * Description      operation completed.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_ignore_op_cmpl(UNUSED_ATTR tBTA_GATTC_CLCB* p_clcb,
                              tBTA_GATTC_DATA* p_data) {
  /* receive op complete when discovery is started, ignore the response,
      and wait for discovery finish and resent */
  APPL_TRACE_DEBUG("%s: op = %d", __func__, p_data->hdr.layer_specific);
}
/*******************************************************************************
 *
 * Function         bta_gattc_search
 *
 * Description      start a search in the local server cache
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_search(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  tBTA_GATT_STATUS status = GATT_INTERNAL_ERROR;
  tBTA_GATTC cb_data;
  APPL_TRACE_DEBUG("%s: conn_id=%d", __func__, p_clcb->bta_conn_id);
  if (p_clcb->p_srcb && p_clcb->p_srcb->p_srvc_cache) {
    status = BTA_GATT_OK;
    /* search the local cache of a server device */
    bta_gattc_search_service(p_clcb, p_data->api_search.p_srvc_uuid);
  }
  cb_data.search_cmpl.status = status;
  cb_data.search_cmpl.conn_id = p_clcb->bta_conn_id;

  /* end of search or no server cache available */
  (*p_clcb->p_rcb->p_cback)(BTA_GATTC_SEARCH_CMPL_EVT, &cb_data);
}
/*******************************************************************************
 *
 * Function         bta_gattc_q_cmd
 *
 * Description      enqueue a command into control block, usually because
 *                  discovery operation is busy.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_q_cmd(tBTA_GATTC_CLCB* p_clcb, tBTA_GATTC_DATA* p_data) {
  bta_gattc_enqueue(p_clcb, p_data);
}

/*******************************************************************************
 *
 * Function         bta_gattc_fail
 *
 * Description      report API call failure back to apps
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_fail(tBTA_GATTC_CLCB* p_clcb,
                    UNUSED_ATTR tBTA_GATTC_DATA* p_data) {
  if (p_clcb->status == BTA_GATT_OK) {
    APPL_TRACE_ERROR("operation not supported at current state [%d]",
                     p_clcb->state);
  }
}

/*******************************************************************************
 *
 * Function         bta_gattc_deregister_cmpl
 *
 * Description      De-Register a GATT client application with BTA completed.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_deregister_cmpl(tBTA_GATTC_RCB* p_clreg) {
  tBTA_GATTC_IF client_if = p_clreg->client_if;
  tBTA_GATTC cb_data;
  tBTA_GATTC_CBACK* p_cback = p_clreg->p_cback;

  memset(&cb_data, 0, sizeof(tBTA_GATTC));

  GATT_Deregister(p_clreg->client_if);
  memset(p_clreg, 0, sizeof(tBTA_GATTC_RCB));

  cb_data.reg_oper.client_if = client_if;
  cb_data.reg_oper.status = BTA_GATT_OK;

  if (p_cback) /* callback with de-register event */
    (*p_cback)(BTA_GATTC_DEREG_EVT, &cb_data);

  if (bta_gattc_num_reg_app() == 0 &&
      bta_gattc_cb.state == BTA_GATTC_STATE_DISABLING) {
    bta_gattc_cb.state = BTA_GATTC_STATE_DISABLED;
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_conn_cback
 *
 * Description      callback functions to GATT client stack.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_conn_cback(tGATT_IF gattc_if, const RawAddress& bdaddr,
                                 uint16_t conn_id, bool connected,
                                 tGATT_DISCONN_REASON reason,
                                 tBT_TRANSPORT transport) {
  if (reason != 0) {
    APPL_TRACE_WARNING("%s() - cif=%d connected=%d conn_id=%d reason=0x%04x",
                       __func__, gattc_if, connected, conn_id, reason);
  }

  if (connected)
    btif_debug_conn_state(bdaddr, BTIF_DEBUG_CONNECTED, GATT_CONN_UNKNOWN);
  else
    btif_debug_conn_state(bdaddr, BTIF_DEBUG_DISCONNECTED, reason);

  tBTA_GATTC_DATA* p_buf =
      (tBTA_GATTC_DATA*)osi_calloc(sizeof(tBTA_GATTC_DATA));
  p_buf->int_conn.hdr.event =
      connected ? BTA_GATTC_INT_CONN_EVT : BTA_GATTC_INT_DISCONN_EVT;
  p_buf->int_conn.hdr.layer_specific = conn_id;
  p_buf->int_conn.client_if = gattc_if;
  p_buf->int_conn.role = L2CA_GetBleConnRole(bdaddr);
  p_buf->int_conn.reason = reason;
  p_buf->int_conn.transport = transport;
  p_buf->int_conn.remote_bda = bdaddr;

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_gattc_enc_cmpl_cback
 *
 * Description      encryption complete callback function to GATT client stack.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_enc_cmpl_cback(tGATT_IF gattc_if, const RawAddress& bda) {
  tBTA_GATTC_CLCB* p_clcb =
      bta_gattc_find_clcb_by_cif(gattc_if, bda, BTA_GATT_TRANSPORT_LE);

  if (p_clcb == NULL) return;

#if (BTA_HH_LE_INCLUDED == TRUE)
  /* filter this event just for BTA HH LE GATT client,
   * In the future, if we want to enable encryption complete event
   * for all GATT clients, we can remove this code
   */
  if (!bta_hh_le_is_hh_gatt_if(gattc_if)) {
    return;
  }
#endif

  APPL_TRACE_DEBUG("%s: cif = %d", __func__, gattc_if);

  do_in_bta_thread(FROM_HERE,
                   base::Bind(&bta_gattc_process_enc_cmpl, gattc_if, bda));
}

/*******************************************************************************
 *
 * Function         bta_gattc_process_api_refresh
 *
 * Description      process refresh API to delete cache and start a new
 *                  discovery if currently connected.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_process_api_refresh(const RawAddress& remote_bda) {
  tBTA_GATTC_SERV* p_srvc_cb = bta_gattc_find_srvr_cache(remote_bda);
  tBTA_GATTC_CLCB* p_clcb = &bta_gattc_cb.clcb[0];
  bool found = false;
  uint8_t i;

  if (p_srvc_cb != NULL) {
    /* try to find a CLCB */
    if (p_srvc_cb->connected && p_srvc_cb->num_clcb != 0) {
      for (i = 0; i < BTA_GATTC_CLCB_MAX; i++, p_clcb++) {
        if (p_clcb->in_use && p_clcb->p_srcb == p_srvc_cb) {
          found = true;
          break;
        }
      }
      if (found) {
        bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
        return;
      }
    }
    /* in all other cases, mark it and delete the cache */
    if (p_srvc_cb->p_srvc_cache != NULL) {
      list_free(p_srvc_cb->p_srvc_cache);
      p_srvc_cb->p_srvc_cache = NULL;
    }
  }
  /* used to reset cache in application */
  bta_gattc_cache_reset(remote_bda);
}
/*******************************************************************************
 *
 * Function         bta_gattc_process_srvc_chg_ind
 *
 * Description      process service change indication.
 *
 * Returns          None.
 *
 ******************************************************************************/
bool bta_gattc_process_srvc_chg_ind(uint16_t conn_id, tBTA_GATTC_RCB* p_clrcb,
                                    tBTA_GATTC_SERV* p_srcb,
                                    tBTA_GATTC_CLCB* p_clcb,
                                    tBTA_GATTC_NOTIFY* p_notify,
                                    tGATT_VALUE* att_value) {
  tBT_UUID gattp_uuid, srvc_chg_uuid;
  bool processed = false;
  uint8_t i;

  gattp_uuid.len = 2;
  gattp_uuid.uu.uuid16 = UUID_SERVCLASS_GATT_SERVER;

  srvc_chg_uuid.len = 2;
  srvc_chg_uuid.uu.uuid16 = GATT_UUID_GATT_SRV_CHGD;

  const tBTA_GATTC_CHARACTERISTIC* p_char =
      bta_gattc_get_characteristic_srcb(p_srcb, p_notify->handle);
  if (p_char &&
      bta_gattc_uuid_compare(&p_char->service->uuid, &gattp_uuid, true) &&
      bta_gattc_uuid_compare(&p_char->uuid, &srvc_chg_uuid, true)) {
    if (att_value->len != BTA_GATTC_SERVICE_CHANGED_LEN) {
      APPL_TRACE_ERROR(
          "%s: received malformed service changed indication, skipping",
          __func__);
      return false;
    }

    uint8_t* p = att_value->value;
    uint16_t s_handle = ((uint16_t)(*(p)) + (((uint16_t)(*(p + 1))) << 8));
    uint16_t e_handle = ((uint16_t)(*(p + 2)) + (((uint16_t)(*(p + 3))) << 8));

    APPL_TRACE_ERROR("%s: service changed s_handle:0x%04x e_handle:0x%04x",
                     __func__, s_handle, e_handle);

    processed = true;
    /* mark service handle change pending */
    p_srcb->srvc_hdl_chg = true;
    /* clear up all notification/indication registration */
    bta_gattc_clear_notif_registration(p_srcb, conn_id, s_handle, e_handle);
    /* service change indication all received, do discovery update */
    if (++p_srcb->update_count == bta_gattc_num_reg_app()) {
      /* not an opened connection; or connection busy */
      /* search for first available clcb and start discovery */
      if (p_clcb == NULL || (p_clcb && p_clcb->p_q_cmd != NULL)) {
        for (i = 0; i < BTA_GATTC_CLCB_MAX; i++) {
          if (bta_gattc_cb.clcb[i].in_use &&
              bta_gattc_cb.clcb[i].p_srcb == p_srcb &&
              bta_gattc_cb.clcb[i].p_q_cmd == NULL) {
            p_clcb = &bta_gattc_cb.clcb[i];
            break;
          }
        }
      }
      /* send confirmation here if this is an indication, it should always be */
      GATTC_SendHandleValueConfirm(conn_id, att_value->handle);

      /* if connection available, refresh cache by doing discovery now */
      if (p_clcb != NULL)
        bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_DISCOVER_EVT, NULL);
    }
    /* notify applicationf or service change */
    if (p_clrcb->p_cback != NULL) {
      tBTA_GATTC bta_gattc;
      bta_gattc.remote_bda = p_srcb->server_bda;
      (*p_clrcb->p_cback)(BTA_GATTC_SRVC_CHG_EVT, &bta_gattc);
    }
  }

  return processed;
}
/*******************************************************************************
 *
 * Function         bta_gattc_proc_other_indication
 *
 * Description      process all non-service change indication/notification.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_proc_other_indication(tBTA_GATTC_CLCB* p_clcb, uint8_t op,
                                     tGATT_CL_COMPLETE* p_data,
                                     tBTA_GATTC_NOTIFY* p_notify) {
  APPL_TRACE_DEBUG("%s: check p_data->att_value.handle=%d p_data->handle=%d",
                   __func__, p_data->att_value.handle, p_data->handle);
  APPL_TRACE_DEBUG("is_notify", p_notify->is_notify);

  p_notify->is_notify = (op == GATTC_OPTYPE_INDICATION) ? false : true;
  p_notify->len = p_data->att_value.len;
  p_notify->bda = p_clcb->bda;
  memcpy(p_notify->value, p_data->att_value.value, p_data->att_value.len);
  p_notify->conn_id = p_clcb->bta_conn_id;

  if (p_clcb->p_rcb->p_cback) {
    tBTA_GATTC bta_gattc;
    bta_gattc.notify = *p_notify;
    (*p_clcb->p_rcb->p_cback)(BTA_GATTC_NOTIF_EVT, &bta_gattc);
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_process_indicate
 *
 * Description      process indication/notification.
 *
 * Returns          None.
 *
 ******************************************************************************/
void bta_gattc_process_indicate(uint16_t conn_id, tGATTC_OPTYPE op,
                                tGATT_CL_COMPLETE* p_data) {
  uint16_t handle = p_data->att_value.handle;
  tBTA_GATTC_CLCB* p_clcb;
  tBTA_GATTC_RCB* p_clrcb = NULL;
  tBTA_GATTC_SERV* p_srcb = NULL;
  tBTA_GATTC_NOTIFY notify;
  RawAddress remote_bda;
  tBTA_GATTC_IF gatt_if;
  tBTA_TRANSPORT transport;

  if (!GATT_GetConnectionInfor(conn_id, &gatt_if, remote_bda, &transport)) {
    APPL_TRACE_ERROR("%s indication/notif for unknown app", __func__);
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, handle);
    return;
  }

  p_clrcb = bta_gattc_cl_get_regcb(gatt_if);
  if (p_clrcb == NULL) {
    APPL_TRACE_ERROR("%s indication/notif for unregistered app", __func__);
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, handle);
    return;
  }

  p_srcb = bta_gattc_find_srcb(remote_bda);
  if (p_srcb == NULL) {
    APPL_TRACE_ERROR("%s indication/notif for unknown device, ignore",
                     __func__);
    if (op == GATTC_OPTYPE_INDICATION)
      GATTC_SendHandleValueConfirm(conn_id, handle);
    return;
  }

  p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);

  notify.handle = handle;
  /* if non-service change indication/notification, forward to application */
  if (!bta_gattc_process_srvc_chg_ind(conn_id, p_clrcb, p_srcb, p_clcb, &notify,
                                      &p_data->att_value)) {
    /* if app registered for the notification */
    if (bta_gattc_check_notif_registry(p_clrcb, p_srcb, &notify)) {
      /* connection not open yet */
      if (p_clcb == NULL) {
        p_clcb = bta_gattc_clcb_alloc(gatt_if, remote_bda, transport);

        if (p_clcb == NULL) {
          APPL_TRACE_ERROR("No resources");
          return;
        }

        p_clcb->bta_conn_id = conn_id;
        p_clcb->transport = transport;

        bta_gattc_sm_execute(p_clcb, BTA_GATTC_INT_CONN_EVT, NULL);
      }

      if (p_clcb != NULL)
        bta_gattc_proc_other_indication(p_clcb, op, p_data, &notify);
    }
    /* no one intersted and need ack? */
    else if (op == GATTC_OPTYPE_INDICATION) {
      APPL_TRACE_DEBUG("%s no one interested, ack now", __func__);
      GATTC_SendHandleValueConfirm(conn_id, handle);
    }
  }
}
/*******************************************************************************
 *
 * Function         bta_gattc_cmpl_cback
 *
 * Description      client operation complete callback register with BTE GATT.
 *
 * Returns          None.
 *
 ******************************************************************************/
static void bta_gattc_cmpl_cback(uint16_t conn_id, tGATTC_OPTYPE op,
                                 tGATT_STATUS status,
                                 tGATT_CL_COMPLETE* p_data) {
  tBTA_GATTC_CLCB* p_clcb;
  APPL_TRACE_DEBUG("bta_gattc_cmpl_cback: conn_id = %d op = %d status = %d",
                   conn_id, op, status);

  /* notification and indication processed right away */
  if (op == GATTC_OPTYPE_NOTIFICATION || op == GATTC_OPTYPE_INDICATION) {
    bta_gattc_process_indicate(conn_id, op, p_data);
    return;
  }
  /* for all other operation, not expected if w/o connection */
  else {
    p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
    if (p_clcb == NULL) {
      APPL_TRACE_ERROR("%s: unknown conn_id =  %d, ignore data", __func__,
                       conn_id);
      return;
    }
  }

  /* if over BR_EDR, inform PM for mode change */
  if (p_clcb->transport == BTA_TRANSPORT_BR_EDR) {
    bta_sys_busy(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
    bta_sys_idle(BTA_ID_GATTC, BTA_ALL_APP_ID, p_clcb->bda);
  }

  bta_gattc_cmpl_sendmsg(conn_id, op, status, p_data);
}

/*******************************************************************************
 *
 * Function         bta_gattc_cmpl_sendmsg
 *
 * Description      client operation complete send message
 *
 * Returns          None.
 *
 ******************************************************************************/
static void bta_gattc_cmpl_sendmsg(uint16_t conn_id, tGATTC_OPTYPE op,
                                   tBTA_GATT_STATUS status,
                                   tGATT_CL_COMPLETE* p_data) {
  const size_t len = sizeof(tBTA_GATTC_OP_CMPL) + sizeof(tGATT_CL_COMPLETE);
  tBTA_GATTC_OP_CMPL* p_buf = (tBTA_GATTC_OP_CMPL*)osi_calloc(len);

  p_buf->hdr.event = BTA_GATTC_OP_CMPL_EVT;
  p_buf->hdr.layer_specific = conn_id;
  p_buf->status = status;
  p_buf->op_code = op;

  if (p_data != NULL) {
    p_buf->p_cmpl = (tGATT_CL_COMPLETE*)(p_buf + 1);
    memcpy(p_buf->p_cmpl, p_data, sizeof(tGATT_CL_COMPLETE));
  }

  bta_sys_sendmsg(p_buf);
}

/*******************************************************************************
 *
 * Function         bta_gattc_cong_cback
 *
 * Description      congestion callback for BTA GATT client.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bta_gattc_cong_cback(uint16_t conn_id, bool congested) {
  tBTA_GATTC_CLCB* p_clcb;
  tBTA_GATTC cb_data;

  p_clcb = bta_gattc_find_clcb_by_conn_id(conn_id);
  if (p_clcb != NULL) {
    if (p_clcb->p_rcb->p_cback) {
      cb_data.congest.conn_id = conn_id;
      cb_data.congest.congested = congested;

      (*p_clcb->p_rcb->p_cback)(BTA_GATTC_CONGEST_EVT, &cb_data);
    }
  }
}

static void bta_gattc_phy_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                       uint8_t tx_phy, uint8_t rx_phy,
                                       uint8_t status) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(gatt_if);

  if (!p_clreg || !p_clreg->p_cback) {
    APPL_TRACE_ERROR("%s: client_if=%d not found", __func__, gatt_if);
    return;
  }

  tBTA_GATTC cb_data;
  cb_data.phy_update.conn_id = conn_id;
  cb_data.phy_update.server_if = gatt_if;
  cb_data.phy_update.tx_phy = tx_phy;
  cb_data.phy_update.rx_phy = rx_phy;
  cb_data.phy_update.status = status;
  (*p_clreg->p_cback)(BTA_GATTC_PHY_UPDATE_EVT, &cb_data);
}

static void bta_gattc_conn_update_cback(tGATT_IF gatt_if, uint16_t conn_id,
                                        uint16_t interval, uint16_t latency,
                                        uint16_t timeout, uint8_t status) {
  tBTA_GATTC_RCB* p_clreg = bta_gattc_cl_get_regcb(gatt_if);

  if (!p_clreg || !p_clreg->p_cback) {
    APPL_TRACE_ERROR("%s: client_if=%d not found", __func__, gatt_if);
    return;
  }

  tBTA_GATTC cb_data;
  cb_data.conn_update.conn_id = conn_id;
  cb_data.conn_update.interval = interval;
  cb_data.conn_update.latency = latency;
  cb_data.conn_update.timeout = timeout;
  cb_data.conn_update.status = status;
  (*p_clreg->p_cback)(BTA_GATTC_CONN_UPDATE_EVT, &cb_data);
}
