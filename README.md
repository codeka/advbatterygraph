Advanced Battery Monitor
========================

I used to have a battery usage graph widget on my home page (I won't say which one, but it was quite popular), but after I bought a Nexus 5, it started to have problems -- it would go for hours without updating the grah at all, which made it quite useless. I contacted the developer, but got response, so I decided to write my own.

Enter Advanced Battery Graph. One of the things I wanted to do at the same time is to see whether it would be possible to write a battery monitoring application *without* a background service. Luckily, the Android alarm SDK is quite comprehensive, and allows me to control exactly when the battery level gets checked with a minimum of CPU cycles required (and *waaaaaay* less memory, too!) As an added bonus, it's far more robust than using a long-running service (which might crash).

If you're just interested in using the graph, you can get it at the Play Store here:

[Advanced Battery Graph in the Play Store](https://play.google.com/store/apps/details?id=au.com.codeka.advbatterygraph)
