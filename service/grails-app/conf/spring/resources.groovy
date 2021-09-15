import org.olf.dataimport.internal.titleInstanceResolvers.*
// Place your Spring DSL code here
beans = {
  /* 
    --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- 
    TODO check if this is actually going into Kiwi
    Behaviour pre-Kiwi: IdFirstTIRSImpl
    Behaviour post-Kiwi: TitleFirstTIRSImpl
  */
  titleInstanceResolverService(IdFirstTIRSImpl)
  //titleInstanceResolverService(TitleFirstTIRSImpl)
}
