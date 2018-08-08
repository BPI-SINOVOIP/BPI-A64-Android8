<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright 2014 Google Inc. All Rights Reserved. -->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:import href="default/header.xsl" />
<xsl:import href="default/body.xsl" />
<xsl:import href="default/footer.xsl" />
<xsl:import href="google/default/spam-footer.xsl" />

<xsl:template match="/">
    <html>
    <body>
    <xsl:call-template name="header" />
    <xsl:call-template name="body" />
    <xsl:call-template name="footer" />
    <xsl:call-template name="spam-footer" />
    </body>
    </html>
</xsl:template>

</xsl:stylesheet>
