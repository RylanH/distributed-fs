namespace java edu.washington.edu.cs.cse490h.donut.service
 
struct KeyId {
  1:i64 id
}

struct TNode {
	1:string name
	2:i32 port
	3:KeyId nodeId
}
 
struct DonutData {
	1:bool exists = 1,
	2:optional binary data
}

exception NodeNotFoundException {

}

service KeyLocator {
  
  /** 
   * @param entryId the id we are looking for
   * @return the name of the node that holds the entryId
   *  		 or null if not found.
   */
  TNode findSuccessor(1:KeyId entryId),
  
  TNode getPredecessor() throws (1:NodeNotFoundException e),
  
  void ping(),
  
  DonutData get(1:KeyId entryId),
  
  void put(1:KeyId entryId, DonutData data),
  
  list<TNode> notify(1:TNode n)
  
  list<TNode> getFingers()
}
