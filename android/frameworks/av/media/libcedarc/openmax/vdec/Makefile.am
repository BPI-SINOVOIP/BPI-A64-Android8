
include $(top_srcdir)/Makefile.inc

pkglib_LTLIBRARIES = libOmxVdec.la

libOmxVdec_la_SOURCES = \
						  omx_vdec.cpp \
						  transform_color_format.c 

#vdec/omx_vdec_newDisplay.cpp ## not suport new display (bz)

libOmxVdec_la_CFLAGS = $(CFLAGS_CDCG)
libOmxVdec_la_CPPFLAGS = $(CPPFLAGS_CDCG)
LOCAL_INCLUDE = -I./include \
				-I$(top_srcdir)/include \
				-I$(top_srcdir)/openmax/omxcore/inc \
				-I$(top_srcdir)/base/include \
				-I$(top_srcdir)/ve/include

libOmxVdec_la_CFLAGS += $(LOCAL_INCLUDE)
libOmxVdec_la_CPPFLAGS += $(LOCAL_INCLUDE)


libOmxVdec_la_CFLAGS +=  -DCONF_OMX_USE_SHARE_BUFFER
libOmxVdec_la_CPPFLAGS += -DCONF_OMX_USE_SHARE_BUFFER

#libOmxVdec_la_CFLAGS +=  -DCONF_OMX_ENABLE_EXTERN_MEM
#libOmxVdec_la_CPPFLAGS += -DCONF_OMX_ENABLE_EXTERN_MEM



libOmxVdec_la_LDFLAGS = $(LDFLAGS_CDCG)

libOmxVdec_la_LIBADD = $(top_srcdir)/base/libcdc_base.la \
						$(top_srcdir)/vdecoder/libcdc_vdecoder.la \
						$(top_srcdir)/memory/libcdc_memory.la
LOCAL_LIB = -ldl -lpthread -lcdc_ve -lcdc_videoengine
libOmxVdec_la_LDFLAGS += $(LOCAL_LIB)


