// simple thunk to load the prettify code
orig_onload = window.onload;
window.onload = function() {
// add new stylesheet
var lnk = document.createElement("link");
lnk.setAttribute("rel","stylesheet");
lnk.setAttribute("type","text/css");
lnk.setAttribute("href","doc-files/prettify.css");
document.getElementsByTagName("head")[0].appendChild(lnk);
// call original onload function
orig_onload();
// invoke google's pretty printer
prettyPrint();
}
// done!
