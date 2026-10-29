// add new stylesheet
var lnk = document.createElement("link");
lnk.setAttribute("rel","stylesheet");
lnk.setAttribute("type","text/css");
lnk.setAttribute("href",window['JAVADOC_BASE']+"doc-files/prettify.css");
document.getElementsByTagName("head")[0].appendChild(lnk);
// load pretty print code.
document.write('<script type="text/javascript" src="'+window['JAVADOC_BASE']+'doc-files/prettify.js"></script>');
// invoke google's pretty printer once the page has loaded.
window.addEventListener("load", function() {
    window['prettyPrint']();
});
// done!
