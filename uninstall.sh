#!/bin/bash

INSTALLDIR=/usr/local/polaric-aprsd

/etc/init.d/polaric_aprsd stop

echo 'Removing user...'
userdel polaric

echo 'Removing links...'
rm /etc/init.d/la3t_aprsd
rm /etc/rc0.d/K20aprsd
rm /etc/rc1.d/K20aprsd
rm /etc/rc2.d/S20aprsd
rm /etc/rc3.d/S20aprsd
rm /etc/rc4.d/S20aprsd
rm /etc/rc5.d/S20aprsd
rm /etc/rc6.d/K20aprsd

echo 'Polaric-APRSD is now UNinstalled.'
