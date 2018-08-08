
# define sources root directory before everything
SRC_ROOT := ../..

# include rule.mk
include $(SRC_ROOT)/rule.mk

.PHONY: all
all:
	cp bttester.sh $(BINDIR)/
	cp *.hcd $(BINDIR)/
	cp rtl*_config $(BINDIR)/
	cp rtl*_fw $(BINDIR)/
	cp btmac.txt $(BINDIR)/
