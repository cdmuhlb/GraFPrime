package edu.cornell.cdm89.grafprime

import java.nio.file.Path
import scala.xml.Elem

/**
  * Represents the fully qualified name of an F Prime type.
  */
case class Fqn(namespace: String, name: String) {
  override def toString = namespace + Fqn.delimiter + name
}

object Fqn {
  val delimiter = "::"

  def fromString(str: String, namespace: String): Fqn = {
    val i = str.lastIndexOf(delimiter)
    if (i == -1) {
      Fqn(namespace, str)
    } else {
      Fqn(str.take(i), str.drop(i + delimiter.length))
    }
  }
}

case class Port(name: String, dataType: Fqn, kind: String)

case class ComponentType(name: Fqn, kind: String, ports: Map[String, Port]) {
  def shortName = name.name
}

case class Connection(
    sourceComponent: String,
    sourcePort: String,
    targetComponent: String,
    targetPort: String
)

object FPrimeTopology {
  import scala.xml.XML

  /**
    * Parse an F Prime Topology into an XML tree.
    *
    * @param fprimeDir Root directory of F Prime checkout
    * @param appXml Path to Topology App XML file (may be relative to fprimeDir)
    * @return XML tree of Topology
    */
  def loadAssembly(fprimeDir: Path, appXml: Path): Elem = {
    XML.loadFile(fprimeDir.resolve(appXml).toFile)
  }

  /**
    * Load component definitions imported by an F Prime Topology.
    *
    * @param fprimeDir Root directory of F Prime checkout
    * @param assembly XML tree of Topology
    * @return Map from component type names to component definition objects
    */
  def loadComponentDefs(
      fprimeDir: Path,
      assembly: Elem
  ): Map[Fqn, ComponentType] = {
    (for (ict <- assembly \ "import_component_type") yield {
      val component = XML.loadFile(fprimeDir.resolve(ict.text).toFile)
      val namespace = component \@ "namespace"
      val name = component \@ "name"
      val kind = component \@ "kind"

      val ports = (for (port <- component \ "ports" \ "port") yield {
        val pName = port \@ "name"
        val dataType = port \@ "data_type"
        val kind = port \@ "kind"
        (pName, Port(pName, Fqn.fromString(dataType, namespace), kind))
      }).toMap

      val c = ComponentType(Fqn(namespace, name), kind, ports)
      (c.name, c)
    }).toMap
  }

  /**
    * Load component instances declared in an F Prime Topology.
    *
    * @param assembly XML tree of Topology
    * @param componentDefs Map from component type names to component definition objects
    * @return Map from instance names to component definitions for those instances
    */
  def loadInstances(
      assembly: Elem,
      componentDefs: Map[Fqn, ComponentType]
  ): Map[String, ComponentType] = {
    (for (instance <- assembly \ "instance") yield {
      val namespace = instance \@ "namespace"
      val componentType = instance \@ "type"
      val name = instance \@ "name"
      (name, componentDefs(Fqn(namespace, componentType)))
    }).toMap
  }

  /**
    * Load connections between components declared in an F Prime Topology.
    *
    * @param assembly XML tree of Topology
    * @return Sequence of connections between source and componentn ports
    */
  def loadConnections(assembly: Elem): Seq[Connection] = {
    (for (connection <- assembly \ "connection") yield {
      val src = (connection \ "source").head
      val srcComponent = src \@ "component"
      val srcPort = src \@ "port"
      val target = (connection \ "target").head
      val targetComponent = target \@ "component"
      val targetPort = target \@ "port"
      Connection(srcComponent, srcPort, targetComponent, targetPort)
    })
  }
}

object TopologyGraph {
  import java.io.PrintWriter
  import java.util.EnumSet
  import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
  import org.eclipse.elk.core.RecursiveGraphLayoutEngine
  import org.eclipse.elk.core.data.LayoutMetaDataService
  import org.eclipse.elk.core.math.KVector
  import org.eclipse.elk.core.options.{
    CoreOptions,
    SizeConstraint,
    NodeLabelPlacement,
    PortSide,
    PortConstraints
  }
  import org.eclipse.elk.core.util.BasicProgressMonitor
  import org.eclipse.elk.graph.{ElkNode, ElkPort}
  import org.eclipse.elk.graph.properties.Property
  import org.eclipse.elk.graph.util.ElkGraphUtil
  import scala.collection.mutable

  val classesProp =
    new Property[Set[String]]("edu.cornell.cdm89.classes", Set.empty[String])

  val label2Prop = new Property[String]("edu.cornell.cdm89.label2")

  /**
    * Create an ELK graph corresponding to the connections between component ports in an F Prime Topology.
    * Some node properties have been set to facilitate custotm SVG visualization.
    *
    * @param fprimeDir Root directory of F Prime checkout
    * @param assembly XML tree of Topology
    * @return ELK graph of F Prime Topology
    */
  def makeFprimeGraph(fprimeDir: Path, assembly: Elem): ElkNode = {
    val componentDefs = FPrimeTopology.loadComponentDefs(fprimeDir, assembly)
    val instances = FPrimeTopology.loadInstances(assembly, componentDefs)

    val portMap = new mutable.HashMap[String, ElkPort]
    val g = ElkGraphUtil.createGraph()
    for ((name, c) <- instances) {
      val n = ElkGraphUtil.createNode(g)
      n.setIdentifier(name)
      n.setProperty(
        CoreOptions.NODE_SIZE_CONSTRAINTS,
        EnumSet.of(
          SizeConstraint.NODE_LABELS,
          SizeConstraint.PORTS,
          SizeConstraint.PORT_LABELS,
          SizeConstraint.MINIMUM_SIZE
        )
      )
      n.setProperty(CoreOptions.NODE_SIZE_MINIMUM, new KVector(25, 25))
      n.setProperty(
        CoreOptions.NODE_LABELS_PLACEMENT,
        EnumSet.of(
          NodeLabelPlacement.OUTSIDE,
          NodeLabelPlacement.H_CENTER,
          NodeLabelPlacement.V_TOP
        )
      )
      n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_ORDER)
      n.setProperty(classesProp, Set(c.kind))
      n.setProperty(label2Prop, c.shortName)
      val label = ElkGraphUtil.createLabel(name, n)
      label.setDimensions(150, 12) // Arbitrary
      for ((pName, p) <- c.ports) {
        val port = ElkGraphUtil.createPort(n)
        port.setIdentifier(pName)
        port.setDimensions(10, 10)
        port.setProperty(
          CoreOptions.PORT_BORDER_OFFSET,
          java.lang.Double.valueOf(-5.0)
        )
        if (p.kind == "output") {
          port.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST)
        } else {
          port.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST)
        }
        port.setProperty(classesProp, Set(p.kind))
        portMap(name + "." + pName) = port
        val pLabel = ElkGraphUtil.createLabel(pName, port)
        pLabel.setDimensions(90, 12) // Arbitrary
      }
    }

    for (Connection(srcComponent, srcPort, targetComponent, targetPort) <- FPrimeTopology
           .loadConnections(assembly)) {
      if (instances.contains(srcComponent) &&
          instances(srcComponent).ports.contains(srcPort) &&
          instances.contains(targetComponent) &&
          instances(targetComponent).ports.contains(targetPort)) {
        val edge = ElkGraphUtil.createSimpleEdge(
          portMap(srcComponent + "." + srcPort),
          portMap(targetComponent + "." + targetPort)
        )
      } else {
        // Ports not declared; are they implicit?
        Console.err.println(
          s"Missing: $srcComponent.$srcPort -> $targetComponent.$targetPort"
        )
      }
    }
    g
  }

  /**
    * Write SVG data visualizing an ELK graph, tailored to F Prime topologies.
    *
    * @param g ELK graph to visualize
    * @param out Writer to write SVG data with
    */
  def createSvg(g: ElkNode, out: PrintWriter): Unit = {
    import scala.jdk.CollectionConverters._

    val service = LayoutMetaDataService.getInstance
    service.registerLayoutMetaDataProviders(new LayeredMetaDataProvider)
    val engine = new RecursiveGraphLayoutEngine
    val monitor = new BasicProgressMonitor
    engine.layout(g, monitor)

    out.println(
      s"""<svg version="1.1" xmlns="http://www.w3.org/2000/svg" width="${g.getWidth}px" height="${g.getHeight}px" viewBox="${g.getX} ${g.getY} ${g.getWidth} ${g.getHeight}">"""
    )
    out.println(raw"""  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="7" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" />
    </marker>
  </defs>""")
    out.println(raw"""<style>
  svg {
    background-color: #fdf6e3;
  }
  #arrow {
    fill: #586e75;
  }
  text {
    fill: #586e75;
    font-family: "Fira Sans", "Calibri";
  }
  .node, .port {
    stroke: #657b83;
  }
  .edge {
    stroke: #586e75;
  }
  a:focus .edge {
    stroke: #6c71c4;
    stroke-width: 5px;
  }
  .junction {
    fill: #586e75;
  }
  .node {
    fill: #eee8d5;
  }
  .port {
    fill: #eee8d5;
  }
  .node.active {
    fill: #2aa198;
  }
  .port.async_input {
    fill: #d33682;
  }
  .port.guarded_input {
    fill: #b58900;
  }

/*
text {
  font-family: "Fira Sans", "Calibri";
}
.node, .port {
  fill: white;
  stroke: black;
}
.node.active {
  fill: silver;
}
.port.async_input {
  fill: silver;
}
.port.guarded_input {
  fill: black;
}
*/
</style>""")

    // Edges
    out.println("""<g stroke="black">""")
    for (e <- g.getContainedEdges.asScala) {
      for (s <- e.getSections.asScala) {
        out.println("""<a href="#0">""")
        out.print(s"""<polyline points="${s.getStartX} ${s.getStartY}""")
        for (bend <- s.getBendPoints.asScala) {
          out.print(s", ${bend.getX} ${bend.getY}")
        }
        out.println(
          s""", ${s.getEndX} ${s.getEndY}" fill="none" marker-end="url(#arrow)" class="edge" />"""
        )
      }
      out.println("</a>")
    }
    out.println("</g>")
    // Junction markers
    for (e <- g.getContainedEdges.asScala) {
      for (j <- e.getProperty(CoreOptions.JUNCTION_POINTS).asScala) {
        out.println(
          s"""<circle cx="${j.x}" cy="${j.y}" r="2" class="junction" />"""
        )
      }
    }

    // Nodes and ports
    for (c <- g.getChildren.asScala) {
      out.println(
        s"""<g transform="translate(${c.getX} ${c.getY})" class="node-group">"""
      )
      val nClasses = c.getProperty(classesProp)
      out.println(
        s"""<rect width="${c.getWidth}" height="${c.getHeight}" class="${(nClasses + "node")
          .mkString(" ")}" />"""
      )
      for (lab <- c.getLabels.asScala) {
        out.println(
          s"""<text x="${lab.getX + lab.getWidth / 2}" y="${lab.getY + lab.getHeight}" text-anchor="middle" class="label">${lab.getText}</text>"""
        )
      }
      val lab2 = c.getProperty(label2Prop)
      if (lab2 != null) {
        out.println(
          s"""<text x="${c.getWidth / 2}" y="${c.getHeight / 2 + 6}" text-anchor="middle" class="label2">${lab2}</text>"""
        )
      }
      for (port <- c.getPorts.asScala) {
        val pClasses = port.getProperty(classesProp)
        out.println(
          s"""<rect x="${port.getX}" y="${port.getY}" width="${port.getWidth}" height="${port.getHeight}" class="${(pClasses + "port")
            .mkString(" ")}" />"""
        )
        for (lab <- port.getLabels.asScala) {
          port.getProperty(CoreOptions.PORT_SIDE) match {
            case PortSide.WEST =>
              out.println(
                s"""<text x="${port.getX + lab.getX + lab.getWidth}" y="${port.getY + lab.getY + lab.getHeight}" text-anchor="end">${lab.getText}</text>"""
              )
            case PortSide.EAST =>
              out.println(
                s"""<text x="${port.getX + lab.getX}" y="${port.getY + lab.getY + lab.getHeight}">${lab.getText}</text>"""
              )
            case _ => ??? // Not supported
          }
        }
      }
      out.println("</g>")
    }
    out.println("</svg>")
  }

}
