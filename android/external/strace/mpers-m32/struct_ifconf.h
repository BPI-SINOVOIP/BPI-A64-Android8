#include <inttypes.h>
typedef uint32_t mpers_ptr_t;
typedef
struct {
int32_t ifc_len;
union {
mpers_ptr_t ifcu_buf;
mpers_ptr_t ifcu_req;
} ifc_ifcu;
} ATTRIBUTE_PACKED m32_struct_ifconf;
#define MPERS_m32_struct_ifconf m32_struct_ifconf
