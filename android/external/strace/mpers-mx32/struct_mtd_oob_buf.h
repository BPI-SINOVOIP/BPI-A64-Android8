#include <inttypes.h>
typedef uint32_t mpers_ptr_t;
typedef
struct {
uint32_t start;
uint32_t length;
mpers_ptr_t ptr;
} ATTRIBUTE_PACKED mx32_struct_mtd_oob_buf;
#define MPERS_mx32_struct_mtd_oob_buf mx32_struct_mtd_oob_buf
