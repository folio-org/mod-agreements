import org.olf.dataimport.internal.titleInstanceResolvers.*
import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

// Place your Spring DSL code here
beans = {
  /* 
    --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- 
    Behaviour pre-Lotus: IdFirstTIRSImpl
    Behaviour post-Lotus: TitleFirstTIRSImpl
  */
  //titleInstanceResolverService(IdFirstTIRSImpl)
  //titleInstanceResolverService(TitleFirstTIRSImpl)
  titleInstanceResolverService(WorkSourceIdentifierTIRSImpl)

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
  kbManagementBean(KBManagementBean) {
    ingressType = KBIngressType.Harvest
    //ingressType = KBIngressType.PushKB
  }
}
