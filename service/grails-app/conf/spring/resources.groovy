import org.olf.dataimport.internal.titleInstanceResolvers.*
// Place your Spring DSL code here
beans = {
  /* 
    --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- 
    Behaviour pre-Lotus: IdFirstTIRSImpl
    Behaviour post-Lotus: TitleFirstTIRSImpl
  */
  titleInstanceResolverService(IdFirstTIRSImpl)
  //titleInstanceResolverService(TitleFirstTIRSImpl)
}
