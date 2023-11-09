import org.olf.dataimport.internal.titleInstanceResolvers.*
import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

// Place your Spring DSL code here
beans = {
  /* --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- */
  String TIRS = System.getenv("TIRS")
  switch (TIRS) {
    case 'TitleFirst':
      titleInstanceResolverService(TitleFirstTIRSImpl)
      break;
    case 'WorkSourceIdentifier':
      titleInstanceResolverService(WorkSourceIdentifierTIRSImpl)
      break;
    case 'IdFirst':
    default:
      titleInstanceResolverService(IdFirstTIRSImpl)
      break;
  }
  
  //
  //titleInstanceResolverService(WorkSourceIdentifierTIRSImpl)

  /*
    Diagram of the structure of the TIRSs

 ┌─────────────┐      ┌──────────────┐
 │BaseTIRSUtils├──┬──►│TitleFirstTIRS│
 └─────────────┘  │   └──────────────┘
                  │   ┌───────────┐   ┌────────────────────────┐
                  └──►│IdFirstTIRS├──►│WorkSourceIdentifierTIRS│
                      └───────────┘   └────────────────────────┘
  */

  // Swap between PushKB and Harvest processes to get data into internal KB
  String INGRESS_TYPE = System.getenv("INGRESS_TYPE")
  kbManagementBean(KBManagementBean) {
    switch (INGRESS_TYPE) {
      case 'PushKB':
        ingressType = KBIngressType.PushKB
        break;
      case 'Harvest':
      default:
        ingressType = KBIngressType.Harvest
        break;
    }
  }
}
