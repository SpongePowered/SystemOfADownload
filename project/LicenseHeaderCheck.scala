import sbt._
import sbt.Keys._
import sbt.Path._

import java.nio.file.Files

object LicenseHeaderCheck extends AutoPlugin {

  object autoImport {
    val licenseHeaderCheck = TaskKey[Seq[File]]("licenseHeaderCheck", "Checks that the license is applied correctly to all the needed files")
    val licenseHeaderCheckFiles = SettingKey[Seq[File]]("licenseHeaderCheckFiles", "Files that should have the license header check run")
    val licenseHeaderFile = SettingKey[File]("licenseHeaderFile", "A file giving the license header to check with")
    val licenseHeaderProperties = SettingKey[Map[String, String]]("licenseHeaderProperties", "A map of strings to replace with the given values")
  }
  import autoImport._

  override val projectSettings: Seq[Def.Setting[_]] = Seq(
    licenseHeaderProperties := Map.empty,
    (licenseHeaderCheckFiles / includeFilter) := AllPassFilter,
    (licenseHeaderCheckFiles / excludeFilter) := NothingFilter,
    licenseHeaderCheckFiles := {
      val base = baseDirectory.value
      val include = (licenseHeaderCheckFiles / includeFilter).value
      val exclude = (licenseHeaderCheckFiles / excludeFilter).value

      (base ** include).filter(!exclude.accept(_)).get
    },
    licenseHeaderCheck := {
      val files = licenseHeaderCheckFiles.value
      val headerFile = licenseHeaderFile.value
      val properties = licenseHeaderProperties.value

      val unformattedHeader = Files.lines(headerFile.toPath)
      val formattedHeader = unformattedHeader // TODO


      ???
    }
  )
}
