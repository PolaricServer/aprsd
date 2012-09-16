#!/bin/bash

INSTALLDIR=/usr/local/polaric-aprsd

/etc/init.d/polaric-aprsd stop

echo 'Removing from system...'
rm /etc/logrotate.d/polaric-aprsd
rm /etc/init.d/polaric-aprsd
rm /etc/rc0.d/K20polaric
rm /etc/rc1.d/K20polaric
rm /etc/rc2.d/S20polaric
rm /etc/rc3.d/S20polaric
rm /etc/rc4.d/S20polaric
rm /etc/rc5.d/S20polaric
rm /etc/rc6.d/K20polaric

echo 'Polaric-APRSD is now UNinstalled.'
