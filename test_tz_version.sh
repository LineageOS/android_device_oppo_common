#!/sbin/sh

# ui_print by Chainfire
OUTFD=$(ps | grep -v "grep" | grep -o -E "update_binary(.*)" | cut -d " " -f 3);
ui_print() {
  if [ "$OUTFD" != "" ]; then
    echo "ui_print ${1} " 1>&$OUTFD;
    echo "ui_print " 1>&$OUTFD;
  else
    echo "${1}";
  fi;
}

SRC_IMG=/dev/block/platform/msm_sdcc.1/by-name/tz
TMP_IMG=/tmp/trustzone.img

dd if=$SRC_IMG of=$TMP_IMG
CURRENT_TZ=`strings $TMP_IMG | grep QC_IMAGE_VERSION_STRING | cut -c 25-`
if [ "$CURRENT_TZ" == "" ]; then
  ui_print "ERROR: Unable to determine TrustZone version."
  rm $TMP_IMG
  exit 1
fi

ui_print "Got TrustZone version: $CURRENT_TZ"
rm $TMP_IMG

for TZ_VERSION in "$@"; do
  if [ "$TZ_VERSION" == "$CURRENT_TZ" ]; then
    ui_print "TrustZone version matched, continuing..."
    exit 0
  fi
done

ui_print "TrustZone version outdated, aborting..."
exit 1
