function displayTruth(source, truthValue) {
  if (source == "none") {
    $("#truth-value").text("Unknown");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-unknown");
  } else if (truthValue) {
    $("#truth-value").text("True");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-true");
  } else if (!truthValue) {
    $("#truth-value").text("False");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-false");
  }
}

function loadJustification(elements) {
  $("#justification-toggle-row").show();
  html = '';
  html = html +'<div class="row"><div class="col-md-8 col-md-offset-2 justification">';
  html = html +'<table class="justification-table"><thead><tr>';
  html = html + '<th scope="col" style="width: 50px">Cost So Far</th>';
  html = html + '<th scope="col" style="width: 100px">MacCartney Relation</th>';
  html = html + '<th scope="col">Fact</th></tr></thead>';
  html = html +'<tbody>';
  for (var i = 0; i < elements.length; ++i) {
    html = html + '<tr>';
    html = html +'<td>' + elements[i].cost + '</td>';
    html = html +'<td>' + elements[i].incomingRelation + '</td>';
    html = html +'<td>' + elements[i].gloss + '</td>';
    html = html +'</tr>';
  }
  html = html + '</tbody></table></div></div>';
  $('#justification-container').html(html);
  console.log($('#justification-container').html());
}

function handleError(message) {
  $("#truth-value").removeClass("truth-value-unknown");
  $("#truth-value").removeClass("truth-value-true");
  $("#truth-value").removeClass("truth-value-false");
  $("#truth-value").addClass("truth-value-error")
  $("#truth-value").text("ERROR")
}

function querySuccess(response) {
  console.log(response);
  if (response.success) {
    displayTruth(response.bestResponseSource, response.isTrue);
    loadJustification(response.bestJustification);
  } else {
    handleError(response.errorMessage);
  }
}


$(document).ready(function(){
  jQuery.support.cors = true;
  $("#justification-toggle-row").hide();

  $( "#form-query" ).submit(function( event ) {
    event.preventDefault();
    target = $(this).attr('action');
    getData = $(this).serialize();
    $.ajax({
      url: target,
      data: getData,
      dataType: 'json',
      success: querySuccess
    });
  });

  $( "#justification-toggle" ).click(function(event) {
    event.preventDefault();
    $("#justification-container").collapse('toggle');
    if ($('#justification-toggle-icon').hasClass('glyphicon-expand')) {
      $('#justification-toggle-icon').removeClass('glyphicon-expand');
      $('#justification-toggle-icon').addClass('glyphicon-collapse-down');
    } else {
      $('#justification-toggle-icon').removeClass('glyphicon-collapse-down');
      $('#justification-toggle-icon').addClass('glyphicon-expand');
    }
  })
});