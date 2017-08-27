$(function(){
    $('#upload_btn').click(upload);
});

function upload(){
    var file = $('input[name="upload_file"]').get(0).files[0];

    var formData = new FormData();
    formData.append('file', file);
    
    $("#result_loading").empty();
    $("#result_loading").prepend("<p>Dr.Watson to the rescue. Loading the results ...</p><div align=\"center\"><img src=\"images/loading.gif\" alt=\"loading\" height=\"20%\" width=\"20%\"></img></div>");
    $.ajax({
        url: 'api/analysis/result',
        type: 'POST',
        data: formData,
        cache: false,
        contentType: false,
        processData: false,
        success: function(data){
        	$("#result_loading").empty();
        	$("#sherlock_noresults").empty();
        	$("#results_info").empty();
        	$("#sherlock_conclusions").empty();
			$("#results_info").prepend("<p>Phew ! Good Job Dr. Watson. Moriarty's now in my control and here are the results ...</p>");
        	for (var i=data.length-1; i>=0; i--) {
				$("#sherlock_conclusions").prepend("<div class=\"col-sm-3 w3_tab_img_left\"><div class=\"demo\" class=\"col-sm-3 w3_tab_img_left\"><a class=\"cm-overlay\" href=\"" + data[i].url +"\"><figure class=\"imghvr-shutter-in-out-diag-2\"><img src=\""+ data[i].url + "\" alt=\" \" class=\"img-responsive\" style=\"margin-bottom: 2.5em;margin-top: 2.5em;\"/></figure></a></div><div class=\"agile-gallery-info\"><h5>"+ data[i].name +"</h5><p>Matched :"+ data[i].score +"</p></div></div>");
			}
        	if(data.length==0){$("#sherlock_conclusions").prepend("<p>Didn't find any suspected criminal. All are clean & innocent.<p>")}
        	$("#sherlock_conclusions").prepend("<div class=\"clearfix\"> </div>");
            console.log("SUCCESS : ", data);
        },
        error: function(response){
        	$("#result_loading").empty();
            var error = "error";
            if (response.status === 409){
                error = response.responseText;
            }
            alert(error);
        },
        xhr: function() {
            var myXhr = $.ajaxSettings.xhr();
            if (myXhr.upload) {
                myXhr.upload.addEventListener('progress', progress, false);
            } else {
                console.log('Upload progress is not supported.');
            }
            return myXhr;
        }
    });
}

function progress(e) {
    if (e.lengthComputable) {
        $('#progress_percent').text(Math.floor((e.loaded * 100) / e.total));
        $('progress').attr({value:e.loaded,max:e.total});
    }
}
