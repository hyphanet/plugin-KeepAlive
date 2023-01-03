# Freenet plugin Keep Alive

### Dependencies
- java8 jdk
    - needs to be set as gradle JAVA_HOME
- gradle 4.10.3 (not higher)
- gradle project Freenet REference Daemon (https://github.com/freenet/fred)
    - needs to be next to the plugin-KeepAlive folder
    - you need to `gradle build` the fred project

### For building run
```
gradle jar
```

### Build using an IDE
If you would like to use Eclipse than import the project as "Existing Gradle Project" and set the gradle settings as:
- project specific
- specific gradle version to 4.10.3
- set gradle "Java home" to "*\Eclipse Adoptium\jdk8*" without "\bin"

Also you need to import "Fred" and do the same
