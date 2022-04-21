import org.olf.kb.RemoteKB
log.info "Running specific diku tenant data file"

RemoteKB.findByName('GOKb_TEST') ?: (new RemoteKB(
    name:'GOKb_TEST',
    type:'org.olf.kb.adapters.GOKbOAIAdapter',
    uri:'https://gokbt.gbv.de/gokb/oai/index',
    fullPrefix:'gokb',
    rectype: RemoteKB.RECTYPE_PACKAGE,
    active:Boolean.TRUE,
    supportsHarvesting:true,
    activationEnabled:false
).save(failOnError:true))

/* RemoteKB.findByName('GOKb_Title') ?: (new RemoteKB(
  name:'GOKb_Title',
  type:'org.olf.kb.adapters.GOKbOAIAdapter',
  uri:'https://gokbt.gbv.de/gokb/oai',
  fullPrefix:'gokb',
  rectype: RemoteKB.RECTYPE_TITLE,
  active:Boolean.TRUE,
  supportsHarvesting:true,
  activationEnabled:false
).save(failOnError:true)) */
