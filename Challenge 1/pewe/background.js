
function onrequest(req) {
  console.log("");
  // This function will be called everytime the browser is about to send out an http or https request.
  // The req variable contains all information about the request.
  // If we return {}  the request will be performed, without any further changes
  // If we return {cancel:true} , the request will be cancelled.
  // If we return {requestHeaders:req.requestHeaders} , any modifications made to the requestHeaders (see below) are sent.

  // log what file we're going to fetch:
  console.log("Loading: " + req.method +" "+ req.url + " "+ req.type);

  for (i=0; i<req.requestHeaders.length; i++) {
    //Hide the browser and operating system
    if (req.requestHeaders[i].name.toLowerCase() == "user-agent"){
      req.requestHeaders[i].value = "anonymous";    // Deleting the entry completely causes issues with some websites, so it's replaced with useless information.
      console.log("Hiding browser and operating system.");
    }
    //Hide the website the user is coming from
    else if (req.requestHeaders[i].name.toLowerCase() == "referer") {
      req.requestHeaders.splice(i,1);
      console.log("Hiding refering website.");
    }
  }

  // Hide the website the user is coming from (again)
  if (req.originUrl != undefined) {
    req.originUrl = undefined;
    console.log("Hiding original URL.");
  }

  return {requestHeaders:req.requestHeaders};
}


// no need to change the following, it just makes sure that the above function is called whenever the browser wants to fetch a file
browser.webRequest.onBeforeSendHeaders.addListener(
  onrequest,
  {urls: ["<all_urls>"]},
  ["blocking", "requestHeaders"]
);
