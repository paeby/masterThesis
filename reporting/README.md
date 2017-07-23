# Reporting service for Core Engine (AKA Platform)

You need to have Core Engine (AKA Platform) running before using this service. In particular, CE's Kadfka topics must be created before launcing Reporting. For more info, refer to CE docs : https://bitbucket.org/bestmile/platform

To create the Database tables, type:

```
sbt "run-main com.bestmile.task.ResetDatabase"
```

To run the project:

```
sbt "run-main com.bestmile.WebServer"
```


To package:

```
sbt clean universal:packageBin
```

You can find the generated files here: 

```
target/universal/reporting_1.0.0.zip
```

Two binaries are generated, corresponding to the 2 main classes:

```
bin/web-server
bin/reset-database
```

All shared libraries are in the `lib` folder.

To configure external dependencies, you can overide these environment variables:

```
http {
  hostname = "0.0.0.0"
  hostname = ${?HTTP_HOSTNAME}
  port = 9002
  port = ${?HTTP_PORT}
}

env="dev"
env=${?ENVIRONMENT_ID}

postgresql {
  server {
    hostname = "0.0.0.0"
    hostname = ${?POSTGRESQL_SERVER}
    port     = 5432
    port     = ${?POSTGRESQL_PORT}
  }
  main {
    db       = "reporting_"${env}
    role     = "postgres"
    role     = ${?POSTGRESQL_ROLE}
    password = "postgres"
    password = ${?POSTGRESQL_PASSWORD}
  }
}

kafka {
  brokers = [{
    hostname = "0.0.0.0"
    hostname = ${?KAFKA_SERVER}
    port = 9092
    port =  ${?KAFKA_PORT}
  }]

  topics = {
    bookings = ${env}"-broadcast-cud-bookings"
    vehicleStatus = ${env}"-broadcast-vehicle-attributes"
  }
}
```

