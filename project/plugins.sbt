addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")
addDependencyTreePlugin

resolvers += Resolver.jcenterRepo

addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.8.4")
