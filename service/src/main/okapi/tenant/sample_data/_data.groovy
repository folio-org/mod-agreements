log.info "Running default tenant data file"

import org.olf.kb.RemoteKB

// For the generic setup - we configure GOKB_TEST but set ACTIVE=FALSE

// RemoteKB.findByName('GOKb_TEST') ?: (new RemoteKB(
//     name:'GOKb_TEST',
//     type:'org.olf.kb.adapters.GOKbOAIAdapter',
//     uri:'https://gokbt.gbv.de/gokb/oai/index',
//     fullPrefix:'gokb',
//     rectype: RemoteKB.RECTYPE_PACKAGE,
//     active:Boolean.FALSE,
//     supportsHarvesting:true,
//     activationEnabled:false
// ).save(failOnError:true))

RemoteKB.findByName('GOKb') ?: (new RemoteKB(
    name:'GOKb',
    type:'org.olf.kb.adapters.GOKbOAIAdapter',
    uri:'https://gokb.org/gokb/oai/index',
    fullPrefix:'gokb',
    rectype: RemoteKB.RECTYPE_PACKAGE,
    active:Boolean.FALSE,
    supportsHarvesting:true,
    activationEnabled:false
).save(failOnError:true))


// TIPP Interface https://gokb.org/gokb/api/scroll?component_type=TitleInstancePackagePlatform&changedSince=2022-06-25+00:00:00

RemoteKB.findByName('GOKbTIPP') ?: (new RemoteKB(
    name:'GOKbTIPP',
    type:'org.olf.kb.adapters.GOKbTIPPAdapter',
    uri:'https://gokb.org/gokb/api/scroll',
    fullPrefix:'TitleInstancePackagePlatform',
    rectype: RemoteKB.RECTYPE_TIPP,
    active:Boolean.FALSE,
    supportsHarvesting:true,
    activationEnabled:false
).save(failOnError:true))

