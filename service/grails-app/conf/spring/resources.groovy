import org.olf.dataimport.internal.titleInstanceResolvers.*
import io.swagger.models.Swagger
import io.swagger.models.License
import io.swagger.models.Contact

// Place your Spring DSL code here
beans = {
  /* 
    --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- 
    Behaviour pre-Lotus: IdFirstTIRSImpl
    Behaviour post-Lotus: TitleFirstTIRSImpl
  */
  titleInstanceResolverService(IdFirstTIRSImpl)
  //titleInstanceResolverService(TitleFirstTIRSImpl)

  swagger(Swagger) {
    // securityDefinitions = ["apiKey": new ApiKeyAuthDefinition("apiKey", In.HEADER)]
    // security = [new SecurityRequirement().requirement("apiKey")]
    info = new io.swagger.models.Info()
                  .title('mod-agreements : manage agreements that provide access to colletions of electronic resources for the FOLIO LSP')
                  .description('mod-agreements description')
                  .contact(new Contact()
                    .name("Knowledge Integration Ltd")
                    .url("https://www.k-int.com")
                    .email("info@k-int.com"))
                  .license(new License()
                    .name("Apache License 2.0")
                    .url("http://www.apache.org/licenses/LICENSE-2.0.html"));

  }
}
