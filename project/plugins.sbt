addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")
addDependencyTreePlugin
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

resolvers += Resolver.jcenterRepo

addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.8.4")
