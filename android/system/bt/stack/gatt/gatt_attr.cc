/******************************************************************************
 *
 *  Copyright (C) 2008-2012 Broadcom Corporation
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
 *  this file contains the main GATT server attributes access request
 *  handling functions.
 *
 ******************************************************************************/

#include "bt_target.h"
#include "bt_utils.h"

#include "btcore/include/uuid.h"
#include "gatt_api.h"
#include "gatt_int.h"
#include "osi/include/osi.h"

using base::StringPrintf;

#define GATTP_MAX_NUM_INC_SVR 0
#define GATTP_MAX_CHAR_NUM 2
#define GATTP_MAX_ATTR_NUM (GATTP_MAX_CHAR_NUM * 2 + GATTP_MAX_NUM_INC_SVR + 1)
#define GATTP_MAX_CHAR_VALUE_SIZE 50

#ifndef GATTP_ATTR_DB_SIZE
#define GATTP_ATTR_DB_SIZE                                    \
  GATT_DB_MEM_SIZE(GATTP_MAX_NUM_INC_SVR, GATTP_MAX_CHAR_NUM, \
                   GATTP_MAX_CHAR_VALUE_SIZE)
#endif

static void gatt_request_cback(uint16_t conn_id, uint32_t trans_id,
                               uint8_t op_code, tGATTS_DATA* p_data);
static void gatt_connect_cback(UNUSED_ATTR tGATT_IF gatt_if,
                               const RawAddress& bda, uint16_t conn_id,
                               bool connected, tGATT_DISCONN_REASON reason,
                               tBT_TRANSPORT transport);
static void gatt_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                tGATT_DISC_RES* p_data);
static void gatt_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                 tGATT_STATUS status);
static void gatt_cl_op_cmpl_cback(UNUSED_ATTR uint16_t conn_id,
                                  UNUSED_ATTR tGATTC_OPTYPE op,
                                  UNUSED_ATTR tGATT_STATUS status,
                                  UNUSED_ATTR tGATT_CL_COMPLETE* p_data);

static void gatt_cl_start_config_ccc(tGATT_PROFILE_CLCB* p_clcb);

static tGATT_CBACK gatt_profile_cback = {gatt_connect_cback,
                                         gatt_cl_op_cmpl_cback,
                                         gatt_disc_res_cback,
                                         gatt_disc_cmpl_cback,
                                         gatt_request_cback,
                                         NULL,
                                         NULL,
                                         NULL,
                                         NULL};

/*******************************************************************************
 *
 * Function         gatt_profile_find_conn_id_by_bd_addr
 *
 * Description      Find the connection ID by remote address
 *
 * Returns          Connection ID
 *
 ******************************************************************************/
uint16_t gatt_profile_find_conn_id_by_bd_addr(const RawAddress& remote_bda) {
  uint16_t conn_id = GATT_INVALID_CONN_ID;
  GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &conn_id,
                            BT_TRANSPORT_LE);
  return conn_id;
}

/*******************************************************************************
 *
 * Function         gatt_profile_find_clcb_by_conn_id
 *
 * Description      find clcb by Connection ID
 *
 * Returns          Pointer to the found link conenction control block.
 *
 ******************************************************************************/
static tGATT_PROFILE_CLCB* gatt_profile_find_clcb_by_conn_id(uint16_t conn_id) {
  uint8_t i_clcb;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->conn_id == conn_id) return p_clcb;
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_find_clcb_by_bd_addr
 *
 * Description      The function searches all LCBs with macthing bd address.
 *
 * Returns          Pointer to the found link conenction control block.
 *
 ******************************************************************************/
static tGATT_PROFILE_CLCB* gatt_profile_find_clcb_by_bd_addr(
    const RawAddress& bda, tBT_TRANSPORT transport) {
  uint8_t i_clcb;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (p_clcb->in_use && p_clcb->transport == transport && p_clcb->connected &&
        p_clcb->bda == bda)
      return p_clcb;
  }

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_clcb_alloc
 *
 * Description      The function allocates a GATT profile connection link
 *                  control block
 *
 * Returns          NULL if not found. Otherwise pointer to the connection link
 *                  block.
 *
 ******************************************************************************/
tGATT_PROFILE_CLCB* gatt_profile_clcb_alloc(uint16_t conn_id,
                                            const RawAddress& bda,
                                            tBT_TRANSPORT tranport) {
  uint8_t i_clcb = 0;
  tGATT_PROFILE_CLCB* p_clcb = NULL;

  for (i_clcb = 0, p_clcb = gatt_cb.profile_clcb; i_clcb < GATT_MAX_APPS;
       i_clcb++, p_clcb++) {
    if (!p_clcb->in_use) {
      p_clcb->in_use = true;
      p_clcb->conn_id = conn_id;
      p_clcb->connected = true;
      p_clcb->transport = tranport;
      p_clcb->bda = bda;
      break;
    }
  }
  if (i_clcb < GATT_MAX_APPS) return p_clcb;

  return NULL;
}

/*******************************************************************************
 *
 * Function         gatt_profile_clcb_dealloc
 *
 * Description      The function deallocates a GATT profile connection link
 *                  control block
 *
 * Returns          void
 *
 ******************************************************************************/
void gatt_profile_clcb_dealloc(tGATT_PROFILE_CLCB* p_clcb) {
  memset(p_clcb, 0, sizeof(tGATT_PROFILE_CLCB));
}

/*******************************************************************************
 *
 * Function         gatt_request_cback
 *
 * Description      GATT profile attribute access request callback.
 *
 * Returns          void.
 *
 ******************************************************************************/
static void gatt_request_cback(uint16_t conn_id, uint32_t trans_id,
                               tGATTS_REQ_TYPE type, tGATTS_DATA* p_data) {
  uint8_t status = GATT_INVALID_PDU;
  tGATTS_RSP rsp_msg;
  bool ignore = false;

  memset(&rsp_msg, 0, sizeof(tGATTS_RSP));

  switch (type) {
    case GATTS_REQ_TYPE_READ_CHARACTERISTIC:
    case GATTS_REQ_TYPE_READ_DESCRIPTOR:
      status = GATT_READ_NOT_PERMIT;
      break;

    case GATTS_REQ_TYPE_WRITE_CHARACTERISTIC:
    case GATTS_REQ_TYPE_WRITE_DESCRIPTOR:
      status = GATT_WRITE_NOT_PERMIT;
      break;

    case GATTS_REQ_TYPE_WRITE_EXEC:
    case GATT_CMD_WRITE:
      ignore = true;
      VLOG(1) << StringPrintf("Ignore GATT_REQ_EXEC_WRITE/WRITE_CMD");
      break;

    case GATTS_REQ_TYPE_MTU:
      VLOG(1) << StringPrintf("Get MTU exchange new mtu size: %d", p_data->mtu);
      ignore = true;
      break;

    default:
      VLOG(1) << StringPrintf("Unknown/unexpected LE GAP ATT request: 0x%02x",
                              type);
      break;
  }

  if (!ignore) GATTS_SendRsp(conn_id, trans_id, status, &rsp_msg);
}

/*******************************************************************************
 *
 * Function         gatt_connect_cback
 *
 * Description      Gatt profile connection callback.
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_connect_cback(UNUSED_ATTR tGATT_IF gatt_if,
                               const RawAddress& bda, uint16_t conn_id,
                               bool connected, tGATT_DISCONN_REASON reason,
                               tBT_TRANSPORT transport) {
  VLOG(1) << __func__ << ": from " << bda
          << StringPrintf(" connected:%d conn_id=%d reason = 0x%04x", connected,
                          conn_id, reason);

  tGATT_PROFILE_CLCB* p_clcb =
      gatt_profile_find_clcb_by_bd_addr(bda, transport);
  if (p_clcb == NULL) return;

  if (connected) {
    p_clcb->conn_id = conn_id;
    p_clcb->connected = true;

    if (p_clcb->ccc_stage == GATT_SVC_CHANGED_CONNECTING) {
      p_clcb->ccc_stage++;
      gatt_cl_start_config_ccc(p_clcb);
    }
  } else {
    gatt_profile_clcb_dealloc(p_clcb);
  }
}

/*******************************************************************************
 *
 * Function         gatt_profile_db_init
 *
 * Description      Initializa the GATT profile attribute database.
 *
 ******************************************************************************/
void gatt_profile_db_init(void) {
  tBT_UUID app_uuid = {LEN_UUID_128, {0}};
  uint16_t service_handle = 0;

  /* Fill our internal UUID with a fixed pattern 0x81 */
  memset(&app_uuid.uu.uuid128, 0x81, LEN_UUID_128);

  /* Create a GATT profile service */
  gatt_cb.gatt_if = GATT_Register(&app_uuid, &gatt_profile_cback);
  GATT_StartIf(gatt_cb.gatt_if);

  bt_uuid_t service_uuid;
  uuid_128_from_16(&service_uuid, UUID_SERVCLASS_GATT_SERVER);

  bt_uuid_t char_uuid;
  uuid_128_from_16(&char_uuid, GATT_UUID_GATT_SRV_CHGD);

  btgatt_db_element_t service[] = {
      {.type = BTGATT_DB_PRIMARY_SERVICE, .uuid = service_uuid},
      {.type = BTGATT_DB_CHARACTERISTIC,
       .uuid = char_uuid,
       .properties = GATT_CHAR_PROP_BIT_INDICATE,
       .permissions = 0}};

  GATTS_AddService(gatt_cb.gatt_if, service,
                   sizeof(service) / sizeof(btgatt_db_element_t));

  service_handle = service[0].attribute_handle;
  gatt_cb.handle_of_h_r = service[1].attribute_handle;

  LOG(ERROR) << StringPrintf("gatt_profile_db_init:  gatt_if=%d",
                             gatt_cb.gatt_if);
}

/*******************************************************************************
 *
 * Function         gatt_disc_res_cback
 *
 * Description      Gatt profile discovery result callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_disc_res_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                tGATT_DISC_RES* p_data) {
  tGATT_PROFILE_CLCB* p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return;

  switch (disc_type) {
    case GATT_DISC_SRVC_BY_UUID: /* stage 1 */
      p_clcb->e_handle = p_data->value.group_value.e_handle;
      p_clcb->ccc_result++;
      break;

    case GATT_DISC_CHAR: /* stage 2 */
      p_clcb->s_handle = p_data->value.dclr_value.val_handle;
      p_clcb->ccc_result++;
      break;

    case GATT_DISC_CHAR_DSCPT: /* stage 3 */
      if (p_data->type.uu.uuid16 == GATT_UUID_CHAR_CLIENT_CONFIG) {
        p_clcb->s_handle = p_data->handle;
        p_clcb->ccc_result++;
      }
      break;
  }
}

/*******************************************************************************
 *
 * Function         gatt_disc_cmpl_cback
 *
 * Description      Gatt profile discovery complete callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_disc_cmpl_cback(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                                 tGATT_STATUS status) {
  tGATT_PROFILE_CLCB* p_clcb = gatt_profile_find_clcb_by_conn_id(conn_id);

  if (p_clcb == NULL) return;

  if (status == GATT_SUCCESS && p_clcb->ccc_result > 0) {
    p_clcb->ccc_result = 0;
    p_clcb->ccc_stage++;
    gatt_cl_start_config_ccc(p_clcb);
  } else {
    LOG(ERROR) << StringPrintf(
        "%s() - Unable to register for service changed indication", __func__);
  }
}

/*******************************************************************************
 *
 * Function         gatt_cl_op_cmpl_cback
 *
 * Description      Gatt profile client operation complete callback
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_cl_op_cmpl_cback(UNUSED_ATTR uint16_t conn_id,
                                  UNUSED_ATTR tGATTC_OPTYPE op,
                                  UNUSED_ATTR tGATT_STATUS status,
                                  UNUSED_ATTR tGATT_CL_COMPLETE* p_data) {}

/*******************************************************************************
 *
 * Function         gatt_cl_start_config_ccc
 *
 * Description      Gatt profile start configure service change CCC
 *
 * Returns          void
 *
 ******************************************************************************/
static void gatt_cl_start_config_ccc(tGATT_PROFILE_CLCB* p_clcb) {
  tGATT_DISC_PARAM srvc_disc_param;
  tGATT_VALUE ccc_value;

  VLOG(1) << StringPrintf("%s() - stage: %d", __func__, p_clcb->ccc_stage);

  memset(&srvc_disc_param, 0, sizeof(tGATT_DISC_PARAM));
  memset(&ccc_value, 0, sizeof(tGATT_VALUE));

  switch (p_clcb->ccc_stage) {
    case GATT_SVC_CHANGED_SERVICE: /* discover GATT service */
      srvc_disc_param.s_handle = 1;
      srvc_disc_param.e_handle = 0xffff;
      srvc_disc_param.service.len = 2;
      srvc_disc_param.service.uu.uuid16 = UUID_SERVCLASS_GATT_SERVER;
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_SRVC_BY_UUID, &srvc_disc_param);
      break;

    case GATT_SVC_CHANGED_CHARACTERISTIC: /* discover service change char */
      srvc_disc_param.s_handle = 1;
      srvc_disc_param.e_handle = p_clcb->e_handle;
      srvc_disc_param.service.len = 2;
      srvc_disc_param.service.uu.uuid16 = GATT_UUID_GATT_SRV_CHGD;
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_CHAR, &srvc_disc_param);
      break;

    case GATT_SVC_CHANGED_DESCRIPTOR: /* discover service change ccc */
      srvc_disc_param.s_handle = p_clcb->s_handle;
      srvc_disc_param.e_handle = p_clcb->e_handle;
      GATTC_Discover(p_clcb->conn_id, GATT_DISC_CHAR_DSCPT, &srvc_disc_param);
      break;

    case GATT_SVC_CHANGED_CONFIGURE_CCCD: /* write ccc */
      ccc_value.handle = p_clcb->s_handle;
      ccc_value.len = 2;
      ccc_value.value[0] = GATT_CLT_CONFIG_INDICATION;
      GATTC_Write(p_clcb->conn_id, GATT_WRITE, &ccc_value);
      break;
  }
}

/*******************************************************************************
 *
 * Function         GATT_ConfigServiceChangeCCC
 *
 * Description      Configure service change indication on remote device
 *
 * Returns          none
 *
 ******************************************************************************/
void GATT_ConfigServiceChangeCCC(const RawAddress& remote_bda, bool enable,
                                 tBT_TRANSPORT transport) {
  tGATT_PROFILE_CLCB* p_clcb =
      gatt_profile_find_clcb_by_bd_addr(remote_bda, transport);

  if (p_clcb == NULL)
    p_clcb = gatt_profile_clcb_alloc(0, remote_bda, transport);

  if (p_clcb == NULL) return;

  if (GATT_GetConnIdIfConnected(gatt_cb.gatt_if, remote_bda, &p_clcb->conn_id,
                                transport)) {
    p_clcb->connected = true;
  }
  /* hold the link here */
  GATT_Connect(gatt_cb.gatt_if, remote_bda, true, transport, true);
  p_clcb->ccc_stage = GATT_SVC_CHANGED_CONNECTING;

  if (!p_clcb->connected) {
    /* wait for connection */
    return;
  }

  p_clcb->ccc_stage++;
  gatt_cl_start_config_ccc(p_clcb);
}
