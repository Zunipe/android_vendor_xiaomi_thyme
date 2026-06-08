GAPS_MIN := vendor/xiaomi/thyme/gapps

PRODUCT_COPY_FILES += \
    $(GAPS_MIN)/product/etc/init/gapps.rc:product/etc/init/gapps.rc \
    $(GAPS_MIN)/product/etc/permissions/privapp-permissions-google-product.xml:product/etc/permissions/privapp-permissions-google-product.xml \
    $(GAPS_MIN)/product/etc/sysconfig/google_build.xml:product/etc/sysconfig/google_build.xml \
    $(GAPS_MIN)/product/etc/sysconfig/google.xml:product/etc/sysconfig/google.xml \
    $(GAPS_MIN)/system_ext/etc/permissions/privapp-permissions-google-system-ext.xml:system_ext/etc/permissions/privapp-permissions-google-system-ext.xml

PRODUCT_PACKAGES += \
	GoogleServicesFramework \
	Phonesky \
	GooglePartnerSetup \
	GmsCore
