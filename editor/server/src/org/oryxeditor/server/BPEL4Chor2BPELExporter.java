
package org.oryxeditor.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Import the transformation for BPEL4Chor to BPEL 
 */
import org.oryxeditor.bpel4chor.BPEL4Chor2BPELTopologyAnalyze;
import org.oryxeditor.bpel4chor.BPEL4Chor2BPELGroundingAnalyze;
import org.oryxeditor.bpel4chor.BPEL4Chor2BPELPBDConversion;
import org.oryxeditor.bpel4chor.BPEL4Chor2BPELWSDLCreate;
import org.oryxeditor.bpel4chor.BPELExportPostprocessor;

/**
 * Copyright (c) 2010 
 * 
 * Changhua Li
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class BPEL4Chor2BPELExporter extends HttpServlet {

	
//	private static final long serialVersionUID = 3551528829474652693L;
	private static final long serialVersionUID = -3551528829474652693L;
	
	// use a hash map to record some node informations - with type "participantSet",
	// "associationEdge", "sendOrReceiveActivity", "messageLink" or "process", so that
	// we can easily get all necessary informations about a node
	//
	// in this hash map:
	// key : link ID - type String
	// value : {elementType, nodeElement}  - type Object[]
	private HashMap<String, Object[]> nodeMapForTopology = new HashMap<String, Object[]>();

	// use a hash map to record all link edge informations, so that we can
	// easily get all necessary informations about a link
	//
	// in this hash map:
	// key : link ID - type String
	// value : {senderActivityID, receiverActivityID, messageLinkElement}  
	//         - type Object[]
	private HashMap<String, Object[]> messageLinkMapForTopology = new HashMap<String, Object[]>();

	// use a array list to record all processes with process-paticipantRef relationships
	private ArrayList<String> nonSingleProcessSet = new ArrayList<String>();

	/**
	 * String for BPEL4Chor Parts 
	 * topologyString for topology.xml
	 * groundingString for grounding.bpel
	 * processString for process.bpel 
	 */
	private String topologyString;
	private String groundingString;
	private String processesString;
	
	/**
     * The POST request.
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    	
    	res.setContentType("application/json");
    	PrintWriter out = null;
    	try {
    	    out = res.getWriter();
    	} catch (IOException e) {
    	    e.printStackTrace();
    	}
    	
    	out.print("{\"res\":[");
    	
    	String rdfString = req.getParameter("data");
    	
//    	out.print("!!!!!!"+rdfString+"!!!!!!");
    	
    	String contextPath = getServletContext().getRealPath("");
    	
    	transformTopology (rdfString, out, contextPath);

    	transformGrounding (rdfString, out, contextPath);
    	
    	transformProcesses (rdfString, out, contextPath);
    	
//    	out.print("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//    	out.print(escapeJSON(topologyString));
//    	out.print("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//    	out.print(escapeJSON(groundingString));
//    	out.print("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//    	out.print(escapeJSON(processString));
//    	out.print("???????????????????????????????");
    	ArrayList<String> processesList = separateProcesses(processesString);
//    	out.print(processesList);

    	//convert the BPEL4Chor to BPEL    	
    	try {
			bpel4chorConvertToBPEL(topologyString, groundingString, processesList, out);
		} catch (Exception e) {
			//TODO::Following the exception handling will be done. 
			e.printStackTrace();
		}
		// convert end

    	out.print("]}");
    }
  
	private static String escapeJSON(String json) {
		// escape (some) JSON special characters
		String res = json.replace("\"", "\\\"");
		res = res.replace("\n","\\n");
		res = res.replace("\r","\\r");
		res = res.replace("\t","\\t");
		return res;
	}
	
	/**************************  topology ***********************************/
    private void transformTopology (String rdfString, PrintWriter out, String contextPath){
  	   
    	//System.out.println(rdfString);
	   	// XSLT source
    	final String xsltFilename = contextPath + "/xslt/RDF2BPEL4Chor_Topology.xslt";
	   	final File xsltFile = new File(xsltFilename);
	   	final Source xsltSource = new StreamSource(xsltFile);	
	   	
	   	// Transformer Factory
	   	final TransformerFactory transformerFactory = 
	   			TransformerFactory.newInstance();
	
	   	// Get the rdf source
	   	final Source rdfSource;
	   	InputStream rdf = new ByteArrayInputStream(rdfString.getBytes());
	   	rdfSource = new StreamSource(rdf);
	 
	   	// Get the result string
	   	String bufferResultString = null;
	   	try {
	   		Transformer transformer = transformerFactory
	   					.newTransformer(xsltSource);
	   		StringWriter writer = new StringWriter();
	   		transformer.transform(rdfSource, new StreamResult(writer));
	   		bufferResultString = writer.toString();
	   		String resultString = postprocessTopology(out, bufferResultString);
	   		
	   		//resultString = crossPartnerScopeHandleTopology(resultString) arrange
	   		//               crossPartnerScope node.
		   	topologyString = resultString;           // store the content of the topology into the topologyString
	   		
	   	} catch (Exception e){
	   		handleException(out, "topology", e); 
	   	}
   }
    
    // extended for Cross Partner Scope by Changhua Li
    /**
     * rearrange the content of crossPartnerScopes node, according the content of oldString
     * to create the matched <activities> and <wsp:Plicy>
     * 
     * @param  {String} oldString
     * @return {String} newString
     * @throws Exception 
     */
    private String crossPartnerScopesHandleTopology(String oldString) throws Exception{
    	if(oldString == null || oldString == ""){
    		return oldString;
    	}

 	   	StringWriter stringOut = new StringWriter();
 	   	
		// transform string to document
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputStream oldResultInputStream = new ByteArrayInputStream
							(oldString.getBytes());
		Document oldDocument = builder.parse(oldResultInputStream);
		
		// rearrange crossPartnerScope
		Element topology = getChildElementWithNodeName(oldDocument, 
				"topology", false);
		
		if (topology != null){

			// record necessary informations
			NodeList childrenList = topology.getChildNodes();
			for (int i = 0; i < childrenList.getLength(); i++){
				Node child = childrenList.item(i);
				if (child instanceof Element){
					Element childElement = (Element)child;
					
					if (childElement.getNodeName().equals("participants")
						|| childElement.getNodeName().equals("messageLinks")
						|| childElement.getNodeName().equals("nodeInfoSet")
						|| childElement.getNodeName().equals("associationEdgeInfoSet")){
						recordNodesInfo(childElement);
					}
				}
			}

			// handle each child elements
			childrenList = topology.getChildNodes();
			for (int i = 0; i < childrenList.getLength(); i++){
				Node child = childrenList.item(i);
				if (child instanceof Element){
					Element childElement = (Element)child;
					
					if (childElement.getNodeName().equals("participantTypes")){
						handleParticipantTypesElement(childElement);
					}
					
					if (childElement.getNodeName().equals("participants")){
						handleParticipantsElement(childElement);
					}
					
					if (childElement.getNodeName().equals("messageLinks")){
						handleMessageLinksElement(childElement);
					}
				}
			}
		}	
		// delete all useless attributes and elements
		//cleanUp(topology);
		
		// transform rearranged oldDocument to string
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer();
		DOMSource source = new DOMSource(oldDocument);
		StreamResult result = new StreamResult(stringOut);
		transformer.transform(source, result);
		stringOut.flush();
 
 		return stringOut.toString();
	}  
    // end of extended for Cross Partner Scope by Changhua Li
    
    private String postprocessTopology (PrintWriter out, String oldString) throws Exception{
 	   
    	// initialize
    	nodeMapForTopology.clear();
    	messageLinkMapForTopology.clear();
    	
 	   	StringWriter stringOut = new StringWriter();
 	   	
		// transform string to document
		DocumentBuilderFactory factory = 
			DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputStream oldResultInputStream = new ByteArrayInputStream
							(oldString.getBytes());
		Document oldDocument = builder.parse(oldResultInputStream);
		
		// rearrange document
		Document newDocument = handleTopologyDocument(oldDocument);
		
		// transform document to string
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer();
		DOMSource source = new DOMSource(newDocument);
		StreamResult result = new StreamResult(stringOut);
		transformer.transform(source, result);
		stringOut.flush();
 
 		return stringOut.toString();
    }
    
    private Document handleTopologyDocument(Document oldDocument) {
    	
		Element topology = getChildElementWithNodeName(oldDocument, 
				"topology", false);
		
		if (topology != null){

			// record necessary informations
			NodeList childrenList = topology.getChildNodes();
			for (int i = 0; i < childrenList.getLength(); i++){
				Node child = childrenList.item(i);
				if (child instanceof Element){
					Element childElement = (Element)child;
					
					if (childElement.getNodeName().equals("participants")
						|| childElement.getNodeName().equals("messageLinks")
						|| childElement.getNodeName().equals("nodeInfoSet")
						|| childElement.getNodeName().equals("associationEdgeInfoSet")){
						recordNodesInfo(childElement);
					}
				}
			}

			// handle each child elements
			childrenList = topology.getChildNodes();
			for (int i = 0; i < childrenList.getLength(); i++){
				Node child = childrenList.item(i);
				if (child instanceof Element){
					Element childElement = (Element)child;
					
					if (childElement.getNodeName().equals("participantTypes")){
						handleParticipantTypesElement(childElement);
					}
					
					if (childElement.getNodeName().equals("participants")){
						handleParticipantsElement(childElement);
					}
					
					if (childElement.getNodeName().equals("messageLinks")){
						handleMessageLinksElement(childElement);
					}
				}
			}

			// delete all useless attributes and elements
			cleanUp(topology);
			
		}
		return oldDocument;
	}


	private void recordNodesInfo(Element currentElement) {
		NodeList childrenList = currentElement.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element){
				Element childElement = (Element)child;
				
				String id = childElement.getAttribute("id");
				
				if (childElement.getNodeName().equals("participantSet")
					|| childElement.getNodeName().equals("associationEdge")
					|| childElement.getNodeName().equals("sendOrReceiveActivity")
					|| childElement.getNodeName().equals("messageLink")
					|| childElement.getNodeName().equals("process")){
					
					nodeMapForTopology.put(id, new Object[]{childElement.getNodeName(), childElement});
				}
				
				if (childElement.getNodeName().equals("sendOrReceiveActivity")){
					recordSendActivityOfMessageLink(childElement);
				}
				
				if (childElement.getNodeName().equals("messageLink")){
					recordReceiveActivityOfMessageLink(childElement);
				}
				
				if (childElement.getNodeName().equals("associationEdge")){
					recordTargetOfAssociationEdge(childElement);
				}
			}
		}
	}


	private void recordSendActivityOfMessageLink(Element currentElement) {
		String sendActivityID = currentElement.getAttribute("id");
		
		ArrayList<String> outgoingMessageLinkIDList = getAllOutgoingIDs(currentElement);
		
		Iterator<String> iter = outgoingMessageLinkIDList.iterator();
		while (iter.hasNext()){
			String messageLinkID = iter.next();
			
			String receiveActivityID;
			Element messageLinkElement;
			
			if (messageLinkMapForTopology.containsKey(messageLinkID)){
				receiveActivityID = getReceiveActivityIDInLinkMap(messageLinkID);
				messageLinkElement = getElementInLinkMap(messageLinkID);
			} else {
				receiveActivityID = null;
				messageLinkElement = null;
			}
			
			Object[] newItem = new Object[]{sendActivityID, receiveActivityID, messageLinkElement};
			
			messageLinkMapForTopology.put(messageLinkID, newItem);
		}
	}

	private void recordReceiveActivityOfMessageLink(Element currentElement) {
		String messageLinkID = currentElement.getAttribute("id");
		
		Element targetElement = getChildElementWithNodeName(currentElement, 
				"outgoingLink", false);
		if (targetElement == null) return;
		
		String receiveActivityID = targetElement.getAttribute("targetID");
		if (receiveActivityID == null) return;	
		
		String sendActivityID;
		
		if (messageLinkMapForTopology.containsKey(messageLinkID)){
			sendActivityID = getSendActivityIDInLinkMap(messageLinkID);
		} else {
			sendActivityID = null;
		}
		
		Object[] newItem = new Object[]{sendActivityID, receiveActivityID, currentElement};
		
		messageLinkMapForTopology.put(messageLinkID, newItem);
	}

	private void recordTargetOfAssociationEdge(Element currentElement) {
		
		Element targetElement = getChildElementWithNodeName(currentElement, 
				"outgoingLink", false);
		if (targetElement == null) return;
		
		String targetID = targetElement.getAttribute("targetID");
		if (targetID == null) return;
		
		if (!nonSingleProcessSet.contains(targetID)){
			nonSingleProcessSet.add(targetID);
		}
	}
	
	private void handleParticipantTypesElement(Element participantTypes) {
		
		ArrayList<String> typeRecorder = new ArrayList<String>();
		ArrayList<Element> uselessChildren = new ArrayList<Element>();
		
		NodeList childrenList = participantTypes.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element &&
					child.getNodeName().equals("participantType")){
				
				Element childElement = (Element)child;
				
				// record type
				String type = childElement.getAttribute("name");
				if (typeRecorder.contains(type)){
					uselessChildren.add(childElement);
				} else {
					typeRecorder.add(type);
				}
				
				String processName = childElement
							.getAttribute("participantBehaviorDescription");
				if (processName != null){
					String namespace = childElement.getAttribute("processNamespace");
					String prefix = childElement.lookupPrefix(namespace);
					
					if (prefix != null){
						childElement.setAttribute("participantBehaviorDescription", 
								prefix + ":" + processName);
					}
				}
				
			}
		}
		
		Element movingChild;
		Iterator<Element> movingList = uselessChildren.iterator();
		while (movingList.hasNext()){
			movingChild = movingList.next();
			participantTypes.removeChild(movingChild);
		}
	}



	private void handleParticipantsElement(Element participants) {
		
		ArrayList<Element> deletingList = new ArrayList<Element>();
		
		// handle single process
		NodeList childrenList = participants.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element &&
					(child.getNodeName().equals("participant"))){
				
				// only process has an "id" attribute here
				String id = ((Element) child).getAttribute("id");
				if (id != null && nonSingleProcessSet.contains(id)){
					deletingList.add((Element)child);	
				}
			}
		}
		
		Iterator<Element> deletingListIter = deletingList.iterator();
		while (deletingListIter.hasNext()){
			Element deletingItem = deletingListIter.next();
			participants.removeChild(deletingItem);
		}
		
		
		
		ArrayList<Element[]> movingList = new ArrayList<Element[]>();
		
		childrenList = participants.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element &&
					(child.getNodeName().equals("participant")
							|| child.getNodeName().equals("participantSet"))){
				
				Element currentElement = (Element)child;
				ArrayList<String> outgoingEdges = getAllOutgoingIDs(currentElement);
				Iterator<String> iter = outgoingEdges.iterator();
				while (iter.hasNext()){
					String edgeID = iter.next();
				
					String connectedTargetID = getTargetIdOfAssociationEdge(edgeID);
					String connectedTargetType = getElementTypeInNodeMap(connectedTargetID);
					
					if (connectedTargetType.equals("participantSet")){
						currentElement.removeAttribute("type");
						Element connectedTarget = getElementInNodeMap(connectedTargetID);				
						
						Element[] newMovingItem = new Element[]{currentElement, connectedTarget};
						movingList.add(newMovingItem);
					}
					
				}
				
			}
		}
		
		Iterator<Element[]> movingListIter = movingList.iterator();
		while (movingListIter.hasNext()){
			Element[] movingItem = movingListIter.next();
			
			Element movingElement = movingItem[0];
			Element parentElement = movingItem[1];
			
			moveElementIntoElement(parentElement, movingElement);
		}
	}

	private void moveElementIntoElement(Element parentElement,
			Element movingElement) {
		// remove element from old parent
		Element oldParent = (Element)movingElement.getParentNode();
		oldParent.removeChild(movingElement);
		
		// append it into new parent
		parentElement.appendChild(movingElement);
	}

	private void handleMessageLinksElement(Element messageLinksElement) {
		
		ArrayList<Element> uselessLinks = new ArrayList<Element>();
	
		// in this hash map:
		// key :  link name - type String
		// value : messageLinkElement - type Element
		HashMap<String, Element> linkNameRecorder = new HashMap<String, Element>();
		linkNameRecorder.clear();
		
		Set<String> messageLinkIDSet = messageLinkMapForTopology.keySet();
		Iterator<String> IDSetIter = messageLinkIDSet.iterator();
		while (IDSetIter.hasNext()){
			String currentMessageLinkID = IDSetIter.next();
			
			Element currentMessageLink = getElementInLinkMap(currentMessageLinkID);
			String name = currentMessageLink.getAttribute("name");
			
			if (!linkNameRecorder.containsKey(name)){
				linkNameRecorder.put(name, currentMessageLink);
			} else {
				uselessLinks.add(currentMessageLink);
			}
			
			String sender =  currentMessageLink.getAttribute("senders");
			String receiver =  currentMessageLink.getAttribute("receivers");
			String sendActivityID = getSendActivityIDInLinkMap(currentMessageLinkID);
			String receiveActivityID = getReceiveActivityIDInLinkMap(currentMessageLinkID);

			
			Element messageLink = linkNameRecorder.get(name);

			// add send- and receiveActivity informations for messageLink
			Element sendElement = getElementInNodeMap(sendActivityID);
			String sendActivity =  sendElement.getAttribute("activityName");
			
			Element receiveElement = getElementInNodeMap(receiveActivityID);
			String receiveActivity =  receiveElement.getAttribute("activityName");
			
			
			String currentSenders = messageLink.getAttribute("senders");
			String currentSendActivities = messageLink.getAttribute("sendActivities");
			String currentReceivers = messageLink.getAttribute("receivers");
			String currentReveiveActivities = messageLink.getAttribute("reveiveActivities");
			
			String newSenders = addItemInString(sender, currentSenders);
			String newSendActivities = addItemInString(sendActivity, currentSendActivities);
			String newReceivers = addItemInString(receiver, currentReceivers);
			String newReveiveActivities = addItemInString(receiveActivity, currentReveiveActivities);
			
			messageLink.setAttribute("senders", newSenders);
			messageLink.setAttribute("sendActivities", newSendActivities);
			messageLink.setAttribute("receivers", newReceivers);
			messageLink.setAttribute("reveiveActivities", newReveiveActivities);
		}
		
		Iterator<Element> movingList = uselessLinks.iterator();
		while (movingList.hasNext()){
			Element movingLink = movingList.next();
			messageLinksElement.removeChild(movingLink);
		}
		
		NodeList childrenList = messageLinksElement.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element &&
					child.getNodeName().equals("messageLink")){
				
				Element messageLink = (Element)child;
				
				String currentSenders = messageLink.getAttribute("senders");
				String currentReceivers = messageLink.getAttribute("receivers");
				String currentSendActivities = messageLink.getAttribute("sendActivities");
				String currentReveiveActivities = messageLink.getAttribute("reveiveActivities");
				
				if (currentSenders != null && !currentSenders.contains(" ")){
					messageLink.setAttribute("sender", currentSenders);
					messageLink.removeAttribute("senders");
				}
				
				if (currentReceivers != null && !currentReceivers.contains(" ")){
					messageLink.setAttribute("receiver", currentReceivers);
					messageLink.removeAttribute("receivers");
				}
				
				if (currentSendActivities != null && !currentSendActivities.contains(" ")){
					messageLink.setAttribute("sendActivity", currentSendActivities);
					messageLink.removeAttribute("sendActivities");
				}
				
				if (currentReveiveActivities != null && !currentReveiveActivities.contains(" ")){
					messageLink.setAttribute("reveiveActivity", currentReveiveActivities);
					messageLink.removeAttribute("reveiveActivities");
				}
			}
		}
	}

	private String addItemInString(String newItem, String oldString) {
		if (newItem == null){
			return oldString;
		}
		
		if (oldString.equals("")){
			return newItem;
		}
		
		String[] items = oldString.split(" ");
		for (int i = 0; i < items.length; i++){
			String currentItem = items[i];
			if (currentItem.equals(newItem)){
				return oldString;
			}
		}
		
		String result = oldString + " " + newItem; 
		return result;
	}

	private void cleanUp(Node currentNode) {

		if (!(currentNode instanceof Element)) {
			return;
		};
		
		if (currentNode instanceof Element){
			((Element)currentNode).removeAttribute("id");
			((Element)currentNode).removeAttribute("processNamespace");	
		}
		
		NodeList childNodes = currentNode.getChildNodes();
		ArrayList<Node> uselessChildren = new ArrayList<Node>();
		Node child;
		for (int i=0; i<childNodes.getLength(); i++){
			child = childNodes.item(i);
			if (child instanceof Element){
				if (child.getNodeName().equals("outgoingLink")
						|| child.getNodeName().equals("nodeInfoSet")
						|| child.getNodeName().equals("associationEdgeInfoSet")){
					uselessChildren.add(child);
				} else {
					cleanUp(child);
				}
			}
		}	
		
		Iterator<Node> iter = uselessChildren.iterator();
		Node uselessChild;
		while (iter.hasNext()){
			uselessChild = iter.next();
			currentNode.removeChild(uselessChild);
		}
	}


	private ArrayList<String> getAllOutgoingIDs(Element currentElement) {
		ArrayList<String> result = new ArrayList<String>();
		
		NodeList childrenList = currentElement.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element &&
					child.getNodeName().equals("outgoingLink")){
				
				Element outgoingLink = (Element)child;
				String targetID = outgoingLink.getAttribute("targetID");
				result.add(targetID);
			}
		}
		
		return result;
	}

	
	private Element getChildElementWithNodeName(Node currentNode, 
			String childName, boolean ifNullBuildNewElement) {
		
		NodeList childrenList = currentNode.getChildNodes();
		for (int i = 0; i < childrenList.getLength(); i++){
			Node child = childrenList.item(i);
			if (child instanceof Element && child.getNodeName().equals(childName)){
				return (Element)child;
			}
		}
		
		// if no such child element can be found
		if (ifNullBuildNewElement){
			Element newNode = currentNode.getOwnerDocument()
								.createElement(childName);
			Node firstChild = currentNode.getFirstChild();
			currentNode.insertBefore(newNode, firstChild);
			return newNode;
		} else {
			return null;
		}
	}
	
	private String getTargetIdOfAssociationEdge(String associationEdgeID) {
		Element associationEdge  = getElementInNodeMap(associationEdgeID);
		
		if (associationEdge == null){
			return null;
		}
		
		Element target = getChildElementWithNodeName(associationEdge, 
								"outgoingLink", false);
		
		if (target == null){
			return null;
		} else {
			return target.getAttribute("targetID");
		}
	}

	private String getElementTypeInNodeMap(String nodeID) {
		Object[] infoSet = nodeMapForTopology.get(nodeID);
		return (String)infoSet[0];
	}

	private Element getElementInNodeMap(String nodeID) {
		Object[] infoSet = nodeMapForTopology.get(nodeID);
		return (Element)infoSet[1];
	}

	private String getSendActivityIDInLinkMap(String messageLinkID) {
		Object[] infoSet = messageLinkMapForTopology.get(messageLinkID);
		return (String)infoSet[0];
	}
	
	private String getReceiveActivityIDInLinkMap(String messageLinkID) {
		Object[] infoSet = messageLinkMapForTopology.get(messageLinkID);
		return (String)infoSet[1];
	}
	
	private Element getElementInLinkMap(String messageLinkID) {
		Object[] infoSet = messageLinkMapForTopology.get(messageLinkID);
		return (Element)infoSet[2];
	}
	
	/**************************  grounding *****************************/
    private void transformGrounding (String rdfString, PrintWriter out, String contextPath){
  	   
	   	// XSLT source
    	final String xsltFilename = contextPath + "/xslt/RDF2BPEL4Chor_Grounding.xslt";
	   	final File xsltFile = new File(xsltFilename);
	   	final Source xsltSource = new StreamSource(xsltFile);	
	   	
	   	// Transformer Factory
	   	final TransformerFactory transformerFactory = 
	   			TransformerFactory.newInstance();
	
	   	// Get the rdf source
	   	final Source rdfSource;
	   	InputStream rdf = new ByteArrayInputStream(rdfString.getBytes());
	   	rdfSource = new StreamSource(rdf);
	 
	   	// Get the result string
	   	String resultString = null;
	   	try {
	   		Transformer transformer = transformerFactory
	   						.newTransformer(xsltSource);
	   		StringWriter writer = new StringWriter();
	   		transformer.transform(rdfSource, new StreamResult(writer));
	   		resultString = writer.toString();
	   		
	   		groundingString = resultString;				// Store the content of grounding in groundingString	
	   		
	   	} catch (Exception e){
	   		handleException(out, "grounding", e);
	   	}
   }

    /****************************  processes  *******************************/
    private void transformProcesses (String rdfString, PrintWriter out, String contextPath){

    	// Get the rdf source
    	final Source rdfSource;
    	InputStream rdf = new ByteArrayInputStream(rdfString.getBytes());
    	rdfSource = new StreamSource(rdf);

    	// RDF2BPEL XSLT source
    	String xsltFilename = contextPath + "/xslt/RDF2BPEL.xslt";

    	final File xsltFile = new File(xsltFilename);
    	final Source xsltSource = new StreamSource(xsltFile);	

    	// Transformer Factory
    	final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    	// Get the result string
    	String resultString = null;
    	try {
    		Transformer transformer = transformerFactory.newTransformer(xsltSource);
    		StringWriter writer = new StringWriter();
    		transformer.transform(rdfSource, new StreamResult(writer));
    		resultString = writer.toString();
    	} catch (Exception e){
    		handleException(out, "processes", e); 
    		return;
    	}

    	//System.out.println(resultString);
    	if (resultString != null){
    		try {
    			// do a post-processing on this result bpel document
    			// in this postprocessor the following works will be done:
    			//  	1. rearrange the position of nodes on the basis of their
    			//         bounding. (except child nodes under <flow>)
    			//  	2. rearrange the position of child nodes of <flow> on the
    			//         basis of the order of <link>
    			//      3. separate the first <elseIF> element under <if>-block
    			//         to <condition> and <activity> element
    			//      4. remove all useless attributes and elements, which contain
    			//         the necessary informations for the above works but useless
    			//         right now
    			resultString = postprocessResult(out, resultString);

    			processesString = resultString;		// Store the content of process in processString
    			return;
    		} catch (Exception e){
    			handleException(out, "processes", e); 
    		}
    	}
    }

    private String postprocessResult (PrintWriter out, String oldString){

    	StringWriter stringOut = new StringWriter();
    	try {
    		// transform string to document
    		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    		DocumentBuilder builder = factory.newDocumentBuilder();
    		InputStream oldResultInputStream = new ByteArrayInputStream(oldString.getBytes());
    		Document oldDocument = builder.parse(oldResultInputStream);


    		BPELExportPostprocessor postprocessor = new BPELExportPostprocessor();

    		// rearrange document
    		Document newDocument = postprocessor.postProcessDocument(oldDocument);

    		// transform document to string
    		TransformerFactory tFactory = TransformerFactory.newInstance();
    		Transformer transformer = tFactory.newTransformer();
    		DOMSource source = new DOMSource(newDocument);
    		StreamResult result = new StreamResult(stringOut);
    		transformer.transform(source, result);
    		stringOut.flush();

    	} catch (Exception e){
    		handleException(out, "processes", e); 
    	}

    	return stringOut.toString();

    }
    
/***************************** print methods ********************************/
   private void printResponse(PrintWriter out, String type, String text){
		out.print("{\"type\":\"" + type + "\",");
		out.print("\"success\":true,");
		out.print("\"content\":\"");
		out.print(escapeJSON(text));
		out.print("\"}");
    }
    
    
    private void printError(PrintWriter out, String type, String err){
		out.print("{\"type\":\"" + type+ "\",");
		out.print("\"success\":false,");
		out.print("\"content\":\"");
		out.print(escapeJSON(err));
		out.print("\"}");
    }
    
	private void handleException(PrintWriter out, String type, Exception e) {
		e.printStackTrace();
		printError(out, type, e.getLocalizedMessage());
	}
	
	/**
	 * To separate the processes string(include many processes of BPEL4Chor) into each of them.  
	 * @param resultString
	 * @return
	 */
	private static ArrayList<String> separateProcesses (String resultString){
		   ArrayList<String> resultList = new ArrayList<String>();
		   int indexOfProcess = resultString.indexOf("<process");
		   int indexOfEndProcess = 0;
		   
		   while (indexOfProcess != -1){
			   indexOfEndProcess = resultString.indexOf("process>", indexOfProcess + 1);
			   if (indexOfEndProcess == -1){
				   indexOfEndProcess = resultString.indexOf("/>", indexOfProcess + 1) - 6;
			   }
			   String process = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" 
				   + resultString.substring(indexOfProcess, indexOfEndProcess + 8);
			   
			   resultList.add(process);
			   
			   indexOfProcess = resultString.indexOf("<process", indexOfEndProcess + 1);
		   }
		
		   return resultList;
	   }
	
	/************************* convert BPEL4Chor String to BPEL String **************************/
	private void bpel4chorConvertToBPEL(String topologyString, String groundingString, ArrayList<String> processList, PrintWriter out)
		throws Exception{

		// transform topology and grounding string to document
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputStream TopologyInputStream = new ByteArrayInputStream(topologyString.getBytes());
		InputStream GroundingInputStream = new ByteArrayInputStream(groundingString.getBytes());

		Document docTopo = builder.parse(TopologyInputStream);
		Document docGround = builder.parse(GroundingInputStream);
		
		// call the topology, grounding, processes, wsdl analyze method
		BPEL4Chor2BPELTopologyAnalyze topoAnaly = new BPEL4Chor2BPELTopologyAnalyze();
		BPEL4Chor2BPELGroundingAnalyze groundAnaly = new BPEL4Chor2BPELGroundingAnalyze();
		BPEL4Chor2BPELPBDConversion pbdCon = new BPEL4Chor2BPELPBDConversion();
		BPEL4Chor2BPELWSDLCreate wsdlCreate = new BPEL4Chor2BPELWSDLCreate();

		//topology analyze
		topoAnaly.nsAnalyze(docTopo);
		topoAnaly.paTypeAnalyze(docTopo);
		topoAnaly.paAnalyze(docTopo);
		topoAnaly.mlAnalyze(docTopo);
		topoAnaly.getMl2BindSenderToMap(((Element)docTopo.getFirstChild()));

		//grounding analyze
		groundAnaly.namespacePrefixSet = topoAnaly.namespacePrefixSet;  // will be used in grounding nsAnalyze
		groundAnaly.namespaceSet = topoAnaly.namespaceSet;				// will be used in grounding nsAnalyze
		groundAnaly.ns2prefixMap = topoAnaly.ns2prefixMap;				// will be used in grounding nsAnalyze
		groundAnaly.messageConstructsSet = topoAnaly.messageConstructsSet;
		groundAnaly.messageLinkSet = topoAnaly.messageLinkSet;
		groundAnaly.ml2mcMap = topoAnaly.ml2mcMap;
		groundAnaly.ml2paMap = topoAnaly.ml2paMap; 						// will be used in fparefsML() and in Alg. 3.4
		groundAnaly.ml2bindSenderToMap = topoAnaly.ml2bindSenderToMap; 	// will be used in mlAnalyze
		groundAnaly.pa2scopeMap = topoAnaly.pa2scopeMap; 				// will be used in Alg. 3.4 createPartnerLinkDeclarations
		groundAnaly.paTypeSet = topoAnaly.paTypeSet;                    // will be used in Alg. 3.4 createPartnerLinkDeclarations
		groundAnaly.pa2paTypeMap = topoAnaly.pa2paTypeMap;              // will be used in Alg. 3.4 createPartnerLinkDeclarations
		groundAnaly.paType2processMap = topoAnaly.paType2processMap;    // will be used in Alg. 3.4 createPartnerLinkDeclarations

		groundAnaly.nsAnalyze(docGround);
		groundAnaly.mlAnalyze(docGround);
		groundAnaly.propertyAnalyze(docGround);

		//processes analyze
		Iterator<String> processListIter = processList.iterator();
		String processString = "";
		while (processListIter.hasNext()){
			processString = processListIter.next();
			InputStream ProcessInputStream = new ByteArrayInputStream(processString.getBytes());
			Document docPBD = builder.parse(ProcessInputStream);

			pbdCon.scopeSet = topoAnaly.scopeSet;							
			pbdCon.processSet = topoAnaly.processSet;						
			pbdCon.topologyNS = topoAnaly.topologyNS;						
			pbdCon.forEach2setMap = topoAnaly.forEach2setMap;				
			pbdCon.paSet = topoAnaly.paSet;									
			pbdCon.pa2scopeMap = topoAnaly.pa2scopeMap; 					
			pbdCon.ns2prefixMap = groundAnaly.ns2prefixMap;					
			pbdCon.namespacePrefixSet = groundAnaly.namespacePrefixSet;		
			pbdCon.plSet = groundAnaly.plSet;								
			pbdCon.sc2plMap = groundAnaly.sc2plMap;							
			pbdCon.pl2plTypeMap = groundAnaly.pl2plTypeMap;					
			pbdCon.pl2myRoleMap = groundAnaly.pl2myRoleMap;					
			pbdCon.pl2partnerRoleMap = groundAnaly.pl2partnerRoleMap;		
			pbdCon.messageConstructsSet = groundAnaly.messageConstructsSet;	
			pbdCon.mc2plMap = groundAnaly.mc2plMap;							
			pbdCon.ml2mcMap = groundAnaly.ml2mcMap;							
			pbdCon.messageLinkSet = groundAnaly.messageLinkSet;				
			pbdCon.ml2ptMap = groundAnaly.ml2ptMap;							
			pbdCon.ml2opMap = groundAnaly.ml2opMap;							
			pbdCon.corrPropName2propertyMap = groundAnaly.corrPropName2propertyMap;  
			pbdCon.property2nsprefixOfPropMap = groundAnaly.property2nsprefixOfPropMap; 

			//PBD conversion
			pbdCon.currentDocument = docPBD;
			pbdCon.convertPBD((Element)docPBD.getFirstChild());
			String processName = ((Element)docPBD.getFirstChild()).getAttribute("name");

			//output of the converted PBD
			Source sourceBPEL = new DOMSource(docPBD);
			File bpelFile = new File("/home/eysler/work/DiplomArbeit/oryx-editor/editor/server/src/org/oryxeditor/bpel4chor/testFiles/PBDConvertion" 
					+ processName.toUpperCase() + ".bpel");
			Result resultBPEL = new StreamResult(bpelFile);

			// Write to the BPEL file
			Transformer xformer = TransformerFactory.newInstance().newTransformer();
			xformer.transform(sourceBPEL, resultBPEL);

			// transform document BPEL to string
			StringWriter stringOutBPEL = new StringWriter();
			
			Transformer transformerBPEL = TransformerFactory.newInstance().newTransformer();
			DOMSource sourceDomBPEL = new DOMSource(docPBD);
			StreamResult resultStringBPEL = new StreamResult(stringOutBPEL);
			transformerBPEL.transform(sourceDomBPEL, resultStringBPEL);
			stringOutBPEL.flush();
			out.print("{\"type\":\"bpel\",\"success\":true,\"contentBPEL\":\"" + escapeJSON(stringOutBPEL.toString()) + "\"");
			out.print(',');
			
			//WSDL create
			wsdlCreate.currentDocument = docPBD;
			wsdlCreate.topologyNS = topoAnaly.topologyNS;
			wsdlCreate.plTypeSet = groundAnaly.plTypeSet;
			wsdlCreate.comm2pltMap = groundAnaly.comm2pltMap;
			wsdlCreate.nsprefixSet = pbdCon.namespacePrefixSet;
			wsdlCreate.declarePartnerLinkTypes((Element)docPBD.getFirstChild());
			
			//output of the created WSDL
			Source sourceWSDL = new DOMSource(docPBD);
			File wsdlFile = new File("/home/eysler/work/DiplomArbeit/oryx-editor/editor/server/src/org/oryxeditor/bpel4chor/testFiles/PBDConvertion"
					+ processName.toUpperCase() + ".wsdl");
			Result resultWSDL = new StreamResult(wsdlFile);

			// Write to the WSDL file
			Transformer xformerWSDL = TransformerFactory.newInstance().newTransformer();
			xformerWSDL.transform(sourceWSDL, resultWSDL);

			// transform document WSDL to string
			StringWriter stringOutWSDL = new StringWriter();
			
			Transformer transformerWSDL = TransformerFactory.newInstance().newTransformer();
			DOMSource sourceDomWSDL = new DOMSource(docPBD);
			StreamResult resultStringWSDL = new StreamResult(stringOutWSDL);
			transformerWSDL.transform(sourceDomWSDL, resultStringWSDL);
			stringOutWSDL.flush();
			out.print("\"contentWSDL\":\"" + escapeJSON(stringOutWSDL.toString()) + "\"}");
			
			if (processListIter.hasNext()){
				out.print(',');
			}
		}
	}
}