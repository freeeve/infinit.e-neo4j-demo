package infiniteneo4jdemo

import scala.collection.JavaConverters._
import com.codahale.jerkson.Json._
import dispatch._
import org.anormcypher._
import org.streum.configrity._

object Transfer extends App {
  val config = Configuration.load("transfer.conf")

  val communityId = config[String]("infinite.communityId")
  var infiniteURL = config[String]("infinite.url")
  val apikey = config[String]("infinite.apikey") 

  val neo4jHost = config[String]("neo4j.host")
  val neo4jPath = config[String]("neo4j.path")
  val neo4jPort = config[Int]("neo4j.port")
  
  val requestURL = infiniteURL + communityId + "?infinite_api_key=" + apikey

  val request = url(infiniteURL).POST <:< Map("accept" -> "application/json", "content-type" -> "application/json")

  // a very generic query--this should get everything!
  // perhaps we should limit to a particular date range, in a real batch transfer app
  request.setBody("""
    {
      "entityType" :[ "company", "organization", "person"]
    }
    """)

  val result = Http(request OK as.String).either

  val res = result()
  val strResult = res match {
    case Right(content) => { content; }
    case Left(content)  => { throw new RuntimeException("error:" + content.getMessage) }
  }

  val infiniteResult = parse[InfiniteResponse](strResult)

  Neo4jREST.setServer(neo4jHost, neo4jPort, neo4jPath)

  // loop through documents, load them into neo4j--entities first, then nodes
  // future: might be good to have a community node, so as to have multiple infinit.e communities within a single neo4j database
  for(d <- infiniteResult.data) {
    d match {
      case doc:InfiniteResponseData => handleDocument(doc)
    }
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
      Cypher("""
        START doc=node:node_auto_index(docId={docId})
        CREATE (e {props}),
               (e)-[:is_referred_by]->(doc)
               (doc)-[:references]->(e)
        """).on(
        "docId" -> document.props("docId"),
        "props" -> Map(
          "indexName" -> entity.index,
          "name" -> entity.actual_name,
          "type" -> entity.`type`
      )).execute()
    } else {
      false
    }
  }

  def handleAssociation(document:NeoNode, association:InfiniteAssociation):Boolean = {
    println(association)
    // we take the verb and verb category from infinit.e, and merge them together with an underscore, if both exist
    // there's actually probably a better way to do the below code.
    val verb = {
      val v = association.verb.getOrElse("").replaceAll(" ", "_") 
      val vcat = association.verb_category.getOrElse("").replaceAll(" ", "_")
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
          "props" -> Map("type" -> association.assoc_type)
        ).execute()
        println("type: [" + verb + "]" + result)
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
  description:String, 
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
