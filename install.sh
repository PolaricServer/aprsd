#!/bin/bash

INSTALLDIR=/usr/local/polaric-aprsd

echo 'creating links...'
ln -s $(INSTALLDIR)/daemon /etc/init.d/la3t_aprsd
ln -s /etc/init.d/aprsd /etc/rc0.d/K20aprsd
ln -s /etc/init.d/aprsd /etc/rc1.d/K20aprsd
ln -s /etc/init.d/aprsd /etc/rc2.d/S20aprsd
ln -s /etc/init.d/aprsd /etc/rc3.d/S20aprsd
ln -s /etc/init.d/aprsd /etc/rc4.d/S20aprsd
ln -s /etc/init.d/aprsd /etc/rc5.d/S20aprsd
ln -s /etc/init.d/aprsd /etc/rc6.d/K20aprsd

echo 'Polaric-APRSD is now installed.'

if [ ! -f $(INSTALLDIR)/server.ini ]
   cp $(INSTALLDIR)/server.ini.example $(INSTALLDIR)/server.ini
   echo 'Please edit server.ini'
fi


