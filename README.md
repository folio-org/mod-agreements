# mod-agreements

Copyright (C) 2018-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Introduction - developers looking to enhance the resources that mod-agreements provides

Mod-Agreements is a FOLIO module to manage agreements which control an institutions access to resources. Normally, this will be 
subscribed access to packages of electronic resources, although the aim is to make the affordances offered by mod-agreements as general as possible.

Mod-Agreements can create agreements that control access to content bundled into packages and defined in knowledgebase systems, it can 
identify specific electronic or print resources, and act as a bridge between those resources and associated licenses and purchase documents.

Developers looking to access the services exposed by mod-agreements can find more information in the following sections

## Module installation and upgrade notes

The module has important dependences on reference data. initial installations and module upgrades should specify loadReference=true. The module
may not work as expected if this is omitted.

While this README and the module description template offer some guidance on how to run this module and the resourcing required, it is not possible to anticipate all possible environmental configurations and deployment processes. Determining the exact resourcing, deployment processes and other aspects such as the size of database conneciton pools needed in any particular environment is down to those running the module and it is recommended that all such practices are fully documented by those responsible
### Environment variables 
This is a NON-EXHAUSTIVE list of environment variables which tweak behaviour in this module

| Variable                        | Description                                                                                                                                                                                                                                                                          | Options                                                                                                                                              | Default                       |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|
| `TIRS`                          | Allows the switching of the default "matching" logic underpinning when we declare two incoming titles as equivalent                                                                                                                                                                  | <ul><li>`'IdFirst'`</lit><li>`'TitleFirst'`</lit><li>`'WorkSourceIdentifier'`</li></ul>                                                              | `WorkSourceIdentifier`        |
| `SYNC_PACKAGES_VIA_HARVEST`     | Allows the turning on/off of "sync" for packages harvested via the GokbOAIAdapter                                                                                                                                                                                                    | <ul><li>`'true'`</li><li>`'false'`</li></ul>                                                                                                         | `'false'`                     |
| `INGRESS_TYPE`                  | Allows the switching between the two main ingress methods to get packages/titles into the local KB.These are mutually exclusive options, which is why they are surfaced as an environment variable                                                                                   | <ul><li>`PushKB`</li><li>`Harvest`</li></ul>                                                                                                         | `Harvest` (Subject to change) |
| `KB_HARVEST_BUFFER`             | Allows changing of the default time it takes for mod-agreements to consider its local KB "stale". Note that this will _not_ change the frequency with which the Jobs are created in the system, simply the rate at which those jobs will finish without doing work.                  | <ul><li>`ONE_HOUR`</li><li>`ZERO`</li><li>Any integer -- representing milliseconds</li></ul>                                                         | `1*60*60*1000` (`ONE_HOUR`)   |
| `GLOBAL_S3_SECRET_KEY`          | Allows the setting of a global S3 secret key fallback. First module checks older S3SecretKey AppSetting. If not present then it falls back to this value.                                                                                                                            | S3 secret                                                                                                                                            |                               |
| `ENDPOINTS_INCLUDE_STACK_TRACE` | Allows the HTTP response 500 to contain stacktrace from the exception thrown. Default return will be a generic message and a timestamp.                                                                                                                                              | <ul><li>`true`</li><li>`false`</li></ul>                                                                                                             | `false`                       |
| `DB_MAXPOOLSIZE`                | Configure connection pool to the PG instance for [HikariCP](https://github.com/brettwooldridge/HikariCP). This connection pool will be _doubled_ by each instance, to account for the "system" schema. Details below in the [connection pool issue section](#connection-pool-issues) | Integer. Recommended amount is 50 per instance if GOKB harvesting is configured (see [connection pool issue section](#connection-pool-issues) below) | 10                            |

### Issues
This module has a few "problem" scenarios that _shouldn't_ occur in general operation, their history, reasoning and workarounds are documented below.
#### Locks and failure to upgrade
Particular approaches to upgrades in particular can leave the module unable to self right.
This occurs especially often where the module or container die/are killed regularly shortly after/during the upgrade.
The issue documented here was exacerbated by transaction handling changes brought about by the grails 5 -> 6 upgrade as part of Quesnalia, and fix attempts are ongoing.

In order of importance to check:

- **CPU resource**
  - In the past we have had these particular issues reported commonly where the app was not getting enough CPU resources to run. Please ensure that the CPU resources being allocated to the application are sufficient, see the requisite module requirements for the version running ([Ramsons example matrix](https://folio-org.atlassian.net/wiki/spaces/REL/pages/398983244/Ramsons+R2+2024+-+Bugfest+env+preparation+-+Modules+configuration+details?focusedCommentId=608305153))
- **Liquibase**
  - The module uses liquibase in order to facilitate module data migrations
  - Unfortunately this has a weakness to shutdown mid migration.
  - Check `<tenantName>_mod_agreements.tenant_changelog_lock` does not have `locked` set to `true`
    - If it does, the migration (and hence the upgrade itself) have failed, and it is difficult to extricate the module from this scenario.
    - It may be most prudent to revert the data and retry the upgrade.
  - In general, while the module is uploading it is most likely to succeed if after startup and tenant enabling/upgrading through okapi that the module and its container are NOT KILLED for at least 2 minutes.
  - An addition, a death to the module while upgrading could be due to a lack of reasonable resourcing making it to the module
- **Federated changelog lock**
  - The module also has a manual lock which is managed by the application itself.
  - This is to facilitate multiple instances accessing the same data
  - In particular, this lock table "seeds" every 20 minutes or so, and a death in the middle of this _can_ lock up the application (Although it can sometimes self right from here)
  - If the liquibase lock is clear, first try startup and leaving for a good 20 minutes
    - If the module dies it's likely resourcing that's the issue
    - The module may be able to self right
  - If the module cannot self right
    - Check the `mod_agreements__system.system_changelog_lock`
      - The same applies from the above section as this is a liquibase lock, but this is seriously unlikely to get caught as the table is so small
    - Finally check the `mod_agreements__system.federation_lock`
      - If this table has entries, this can prevent the module from any and all operations
      - It should self right from here, even if pointing at dead instances
        - See `mod_agreements__system.app_instance` for a table of instance ids, a killed and restarted module should eventually get cleared from here.
        - It is NOT RECOMMENDED to clear app_instances manually
      - If there are entries in the federated lock table that do not clear after 20 minutes of uninterrupted running then this table should be manually emptied.

#### Connection pool issues
As of Sunflower release, issues with [federated locks](#locks-and-failure-to-upgrade) and connection pools have been ongoing since Quesnalia.
The attempted fixes and history is documented in JIRA ticket [ERM-3851](https://folio-org.atlassian.net/browse/ERM-3851)

Initially the Grails 6 upgrade caused federated lock rows to themselves lock in PG.
A fix was made for Sunflower (v7.2.0) and backported to Quesnalia (v7.0.12) and Ramsons (v7.1.6).
However this fix is both not fully complete, and worsens an underlying connection pool issue.

The connection pool per instance can be configured via the `DB_MAXPOOLSIZE` environment variable.
Since the introduction of module-federation for this module, this has been _doubled_ to ensure connections are available
for the system schema as well. This is necessary as a starved system schema would all but guarantee the fed lock issues
documented above. As a response, our approach was to request more and more connections, memory, and CPU time to lower the
chances of this happening as much as possible.

As of right now, the recommended Sunflower connection pool is 50 per GOKB harvesting instance.
This leads to 100 connections per instance, almost all of which PG will see as idle. The non-dropping of idle connections
is a [chosen behaviour of Hikari](https://www.postgresql.org/message-id/1395487594923-5797135.post@n5.nabble.com) (and so not a bug)
but the sheer volume of them does help point out that something is amiss with our module.

At the moment, although postgres sees most of this pool as idle, Hikari internally believes them to be active, causing
pool starvation unless massively over-resourcing the instance. This in turn locks up the instance entirely and leads to jobs
silently failing

The workarounds here are to over-resource the module, and to restart problematic instances (or all instances)
when this behaviour manifests, or to revert to versions where this is less prevalent (v7.0.10, v7.1.5) and handle the
federated locking issues instead. Obviously these are not proper solutions.

In Trillium (v7.3.0), the aim is both to fix these bugs, and hopefully thus free up the connection pool to an extent that it can be
run with _significantly_ fewer connections, and potentially set up a way for the configured pool size to be mathematically
split between system and module, so as to avoid the doubling of the pool.

The recommendation for the versions containing the fix is to run with a minimum of 15 connections per instance
(Which will be doubled to 30 to account for the system schema).

## Resources exposed by this module

### /erm/sas resource - SubscriptionAgreements

The /erm/sas resource allows module clients to Create, Retrieve, Update and Delete SubscriptionAgreement entities, and to search for SAs. [See the documentation](./doc/subscription_agreement_resource.md)

### /erm/refdataValues

This resource allows module owners to obtain tenant extensible lists of controlled values. The URL pattern is "/erm/refdataValues/$domain/$property" 

As of 2019-02-20 the following are defined:

| Refdata Category                      | URL for values                                           |
|---------------------------------------|----------------------------------------------------------|
| TitleInstance.SubType                 | /erm/refdataValues/TitleInstance/SubType                 |
| TitleInstance.Type                    | /erm/refdataValues/TitleInstance/Type                    |
| InternalContact.Role                  | /erm/refdataValues/InternalContact/Role                  |
| SubscriptionAgreementOrg.Role         | /erm/refdataValues/SubscriptionAgreementOrg/Role         |
| SubscriptionAgreement.AgreementType   | /erm/refdataValues/SubscriptionAgreement/AgreementType   |
| SubscriptionAgreement.RenewalPriority | /erm/refdataValues/SubscriptionAgreement/RenewalPriority |
| Global.Yes_No                         | /erm/refdataValues/Global/Yes_No                         |
| SubscriptionAgreement.AgreementStatus | /erm/refdataValues/SubscriptionAgreement/AgreementStatus |
| Pkg.Type                              | /erm/refdataValues/Pkg/Type                              |
| IdentifierOccurrence.Status           | /erm/refdataValues/IdentifierOccurrence/Status           |

## ModuleDescriptor

https://github.com/folio-org/mod-agreements/blob/master/service/src/main/okapi/ModuleDescriptor-template.json

# For module developers looking to extend or modify the resources presented by this module

This is the main starter repository for the Grails-based OLF - ERM backend modules.

- [Getting started](service/docs/getting-started.md "Getting started")

There are a couple of differing ways to run this module as a developer, either via the (now defunct) vagrant images, or directly against an internal postgres via docker compose.

Longer term there may be official rancher environments offered for dev, but our current workflow does not include those.

## Additional information

### Issue tracker

See project [ERM](https://issues.folio.org/projects/ERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

## Docker compose
This is the current recommended way to develop against this module, with a couple of key caveats. This setup runs the module standalone, without the okapi layer or the rest of FOLIO.
Obviously this prevents full testing and development of permission based issues or any app interaction, but is sufficient for developing anything internal to the module.

### Setup
The devloper will need `docker compose`, which should now be included in relatively new versions of `docker`. If not, either look to update docker or install the compatible standalone `docker-compose`. (Note that this will change the necessary commands later from `docker compose` to `docker-compose`)

### Run command
First ensure that the postgres is up and running, by navigating to `mod-agreements/tools/testing` and running

    docker compose up
Then navigate in a separate terminal to `mod-agreements/service` and run with the `dc` profile

    ./gradlew -Dgrails.env=dc bootRun

This will run the module on port 8080, and it can be directly `curl`ed to on that port without having to pass through an okapi layer.


## Vagrant
Using the now defunct vagrant images for snapshot allows for a full FOLIO system running in a virtual machine. This is highly costly resource-wise, and the lack of up to date images creates issues for development. However it is listed below for posterity.

### Run command
    ./gradlew -Dgrails.env=vagrant-db bootRun

### Initial Setup
Most developers will run some variant of the following commands the first time through

#### In window #1

Start the vagrant image up from the project root

    vagrant destroy
    vagrant up

Sometimes okapi does not start cleanly in the vagrant image - you can check this with

    vagrant ssh

then once logged in

    docker ps

should list running images - if no processes are listed, you will need to restart okapi (In the vagrant image) with

    sudo su - root
    service okapi stop
    service okapi start

Finish the part off with

    tail -f /var/log/folio/okapi/okapi.log

#### In window #2

Build and run mod-agreements stand alone

    cd service
    grails war
    ../scripts/run_external_reg.sh

#### In window #3

Register the module and load some test data

  cd scripts
  ./register_and_enable.sh
  ./dev_submit_pkg.sh
  ./dev_trigger_kb_sync.sh 

#### In window #4

Run up a stripes platform containing erm

See [UI Agreements quick start](https://github.com/folio-org/ui-agreements/blob/master/README.md)

This section is run in a local setup, not from any particular checked out project, YMMV

    cd ../platform/stripes/platform-erm
    stripes serve ./stripes.config.js --has-all-perms



You should get back

Waiting for webpack to build...
Listening at http://localhost:3000

and then be able to access the app

## Kubernetes Deployment Notes

You may need to set `OKAPI_SERVICE_PORT` and/or `OKAPI_SERVICE_HOST` on the mod-agreements container. 

If you create a Service in Kubernetes named "okapi" and expose a port for Hazelcast and a port for HTTP; the Hazelcast port might be the default and mod-agreements will use that default.



## Validating module descriptor
There is a github action created to run the module descriptor validation on `push`, but if a developer wishes to run the validation locally there is some setup that needs doing. The validation script is a Maven plugin which does not work natively with our gradle based apps.
### Maven
Developer will need maven cli `mvn` installed on their machine.
### settings.xml
Create a `settings.xml` file within the "service" directory (DO NOT MERGE THIS) containing the following:
```
<settings>
  <profiles>
    <profile>
      <id>folioMavenProfile</id>
      <pluginRepositories>
        <pluginRepository>
          <id>folio-nexus</id>
          <name>FOLIO Maven repository</name>
          <url>https://repository.folio.org/repository/maven-folio</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>folioMavenProfile</activeProfile>
  </activeProfiles>
</settings>
```
### Running
Finally the developer can run this command from the root directory (ie `mod-agreements` not `mod-agreements/service`)
```
mvn org.folio:folio-module-descriptor-validator:1.0.0:validate -DmoduleDescriptorFile=service/src/main/okapi/ModuleDescriptor-template.json -s service/settings.xml -l validate_module_descriptor_output.txt
```

This will create a file called `validate_module_descriptor_output.txt` containing the output of the validator. The github action does some cleanup and comments the errors on a PR (if present). The `grep`/`sed` commands with regex can be found in the workflow file `.github/validate-module`.

### Resourcing
The "Memory" requirement listed in the module descriptor for this module is unusually high. This is because the memory requirement is made assuming full range of module functionality is in use, including GOKb Harvest and processing of thousands of TIPPs. Installations not making use of the harvest functionality can run with substantially lower memory.
