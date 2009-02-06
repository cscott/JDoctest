// add new stylesheet
var lnk = document.createElement("link");
lnk.setAttribute("rel","stylesheet");
lnk.setAttribute("type","text/css");
lnk.setAttribute("href",window['JAVADOC_BASE']+"doc-files/prettify.css");
document.getElementsByTagName("head")[0].appendChild(lnk);
// load pretty print code.
document.write('<script type="text/javascript" src="'+window['JAVADOC_BASE']+'doc-files/prettify.js"></script>');
// hook into on-load function.
orig_onload = window.onload;
window.onload = function() {
    orig_onload(); // call original onload function
    // invoke google's pretty printer
    window['prettyPrint']();
}
// done!
