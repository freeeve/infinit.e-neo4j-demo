package infiniteneo4jdemo

import com.codahale.jerkson.Json._
import org.anormcypher._
import com.typesafe.config._
import akka.dispatch.Future
import akka.actor.{Props, ActorSystem}
import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io.IOExtension
import spray.httpx.SprayJsonSupport
import spray.http._

object Transfer extends App {
  val config = ConfigFactory.load();

  val communityId = config.getString("infinite.communityId")
  var infiniteHost = config.getString("infinite.host")
  var infinitePath = config.getString("infinite.path")
  val apikey = config.getString("infinite.apikey") 

  val neo4jHost = config.getString("neo4j.host")
  val neo4jPath = config.getString("neo4j.path")
  val neo4jPort = config.getInt("neo4j.port")

  Neo4jREST.setServer(neo4jHost, neo4jPort, neo4jPath)
  
  val requestURL = infinitePath + communityId + "?infinite_api_key=" + apikey
  println("["+requestURL+"]")

  /* spray client stuff */
  implicit val system = ActorSystem("infinite-request")

  val ioBridge = IOExtension(system).ioBridge

  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioBridge)),
    name = "http-client"
  )

  val conduit = system.actorOf(
    props = Props(new HttpConduit(httpClient, infiniteHost)),
    name = "http-conduit-1"
  )

  val pipeline = HttpConduit.sendReceive(conduit)
  val responseFuture = pipeline(HttpRequest(method = HttpMethods.GET, uri = requestURL))
  responseFuture onComplete {
    case Right(response) =>
      val infiniteResult = parse[InfiniteResponse](response.entity.asString)
      system.stop(conduit) 

      // loop through documents, load them into neo4j--entities first, then nodes
      // future: might be good to have a community node, so as to have multiple infinit.e communities within a single neo4j database
      for(d <- infiniteResult.data) {
        d match {
          case doc:InfiniteResponseData => handleDocument(doc)
         }
      }

      system.shutdown()
    case Left(error) =>
      system.shutdown()
  }

  def handleDocument(document:InfiniteResponseData) = {
    val list = Cypher("START n=node:node_auto_index(docId={id}) return n").on(
      "id" -> document._id)().map(row => 
        row[NeoNode]("n")
      ).toList

    val docNode = if(list.size == 0) {
      Cypher("CREATE (doc {docId:{id}}) return doc").on(
        "id" -> document._id)().map(row =>
        row[NeoNode]("doc")
      ).toList.head
    } else {
      list.head
    }

    for(assoc <- document.associations) {
      handleAssociation(docNode, assoc)
    }

    for(entity <- document.entities) {
      handleEntity(docNode, entity)
    }
  }

  def handleEntity(document:NeoNode, entity:InfiniteEntity):Boolean = {
    val list = Cypher("START n=node:node_auto_index(indexName={name}) return n").on(
      "name" -> entity.index)().map(row => 
      row[NeoNode]("n")
    ).toList

    if(list.size == 0) {
      val result = Cypher("""
        START doc=node:node_auto_index(docId={docId})
        CREATE (e {props}),
               (doc)-[:references]->(e)
        """).on(
        "docId" -> document.props("docId"),
        "props" -> Map(
          "indexName" -> entity.index,
          "name" -> entity.actual_name,
          "type" -> entity.`type`
      )).execute()
      println(entity + "; " + result)
      result
    } else {
      val result = Cypher("""
        START doc=node:node_auto_index(docId={docId}), e=node:node_auto_index(indexName={name})
        CREATE (doc)-[:references]->(e)
        """).on(
        "docId" -> document.props("docId"),
        "name" -> entity.index
      ).execute()
      println(entity + "; (link only) " + result)
      result
    }
  }

  def handleAssociation(document:NeoNode, association:InfiniteAssociation):Boolean = {
    println(association)
    // we take the verb and verb category from infinit.e, and merge them together with an underscore, if both exist
    // there's actually probably a better way to do the below code.
    val verb = {
      val v = association.verb.getOrElse("").replaceAll(" ", "_") 
      val vcat = association.verb_category.getOrElse("").replaceAll(" ", "_").replaceAll("/", "_")
      val tmp = if(v != "" && vcat != "") v + "_" + vcat
                else v + vcat
      if(tmp == "") "association"
      else tmp
    }

    // ew, ugly string concatenation ahead!
    (association.entity1_index, association.entity2_index) match {
      case (Some(e1), Some(e2)) => {
        val result = Cypher("""
          START e1=node:node_auto_index(indexName={e1_indexName}), e2=node:node_auto_index(indexName={e2_indexName}) 
          CREATE e1-[:""" + verb + """ {props} ]->e2
        """).on(
          "e1_indexName" -> e1,
          "e2_indexName" -> e2,
          "props" -> Map("type" -> association.assoc_type,
                         "docId" -> document.props("docId"))
        ).execute()
        println(association + "; " + result)
        result
      }
      case _ => { false }
    }
  }
}

case class InfiniteResponseResponse(action:String, success:Boolean, message:String, time:Long)
case class InfiniteAssociation(
  entity1:Option[String], 
  entity1_index:Option[String], 
  verb:Option[String], 
  verb_category:Option[String], 
  entity2:Option[String], 
  entity2_index:Option[String], 
  assoc_type:String)
case class InfiniteEntity(
  actual_name:String,
  dimension:String,
  disambiguated_name:String,
  doccount:Long,
  frequency:Long,
  index:String,
  relevance:Double,
  totalfrequency:Long,
  `type`:String
)
case class InfiniteResponseData(
  _id:String,
  associations:List[InfiniteAssociation],
  communityId:String,
  created:String,
  description:Option[String], 
  entities:List[InfiniteEntity], 
  mediaType:List[String],
  //metadata:Option[Map[String,Any]],
  modified:String,
  publishedDate:String,
  source:List[String],
  sourceKey:List[String],
  tags:List[String],
  title:String,
  url:String,
  aggregateSignif:Double,
  queryRelevance:Double,
  score:Double
)
case class InfiniteResponse(response:InfiniteResponseResponse, data:List[InfiniteResponseData])
