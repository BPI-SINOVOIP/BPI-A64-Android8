#include <inttypes.h>
typedef uint32_t mpers_ptr_t;
typedef
struct {
uint32_t index;
unsigned char name[32];
uint32_t type;
uint32_t audioset;
uint32_t tuner;
uint64_t std;
uint32_t status;
uint32_t capabilities;
uint32_t reserved[3];
} ATTRIBUTE_PACKED m32_struct_v4l2_input;
#define MPERS_m32_struct_v4l2_input m32_struct_v4l2_input
