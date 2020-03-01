package edu.cornell.cdm89.grafprime

import java.io.PrintWriter
import java.nio.file.Paths

object Main extends App {
  if (args.length < 2 || args.length > 3) {
    Console.err.println("Usage: grafprime <app_xml> <output_file> [fprime_dir]")
    System.exit(1)
  }
  val appXml = Paths.get(args(0))
  val fprimeDir = if (args.length > 2) Paths.get(args(2)) else Paths.get("")
  val assembly = FPrimeTopology.loadAssembly(fprimeDir, appXml)
  val graph = TopologyGraph.makeFprimeGraph(fprimeDir, assembly)
  val out = new PrintWriter(args(1))
  TopologyGraph.createSvg(graph, out)
  out.close()
}
