#!/bin/bash

INSTALLDIR=/usr/local/polaric-aprsd

echo 'Creating links...'
ln -s $(INSTALLDIR)/daemon /etc/init.d/polaric_aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc0.d/K20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc1.d/K20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc2.d/S20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc3.d/S20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc4.d/S20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc5.d/S20aprsd
ln -s /etc/init.d/polaric_aprsd /etc/rc6.d/K20aprsd

echo 'Polaric-APRSD is now installed on your system.'

if [ ! -f $(INSTALLDIR)/server.ini ] ;
   cp $(INSTALLDIR)/server.ini.example $(INSTALLDIR)/server.ini
   echo 'Please edit server.ini'
fi


