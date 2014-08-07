function displayTruth(source, truthValue) {
  if (source == "none") {
    $("#truth-value").text("Unknown");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-unknown");
    $("#truth-value").css("margin-left", "-25px;");
  } else if (truthValue) {
    $("#truth-value").text("True");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-true");
    $("#truth-value").css("margin-left", "15px");
  } else if (!truthValue) {
    $("#truth-value").text("False");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-false");
    $("#truth-value").css("margin-left", "10px;");
  }
}

function getMacCartneyRelation(raw) { return raw.substring(0, 1); }
function getMacCartneyGloss(raw) {
  if (raw.length > 4) {
    return '(' + raw.substring(3, raw.length - 1) + ')';
  } else {
    return '';
  }
}

function loadJustification(elements, truthValue, hasTruthValue, inputQuery) {
  $("#justification-toggle-row").show();
  html = '';
  html = html +'<div class="row"><div class="col-md-8 col-md-offset-2 justification">';

  // Justification Fact
  html = html + '<div class="justification-singleline">';
  html = html + '<span class="justification-singleline-input"> ' + inputQuery + ' </span> is ' + (hasTruthValue ? truthValue : "unknown") + ' because ';
  if (elements.length == 1) {
    html = html + "it's a known OpenIE fact.";
  } else if (hasTruthValue) {
    html = html + 'of the OpenIE fact: <span class="justification-singleline-fact">' + elements[0].gloss + '</span>.';
  } else {
    html = html + "we couldn't find justification for it.";
  }
  html = html + '</div>';

  if (hasTruthValue) {
    // Justification Table
    // (table)
    html = html +'<table class="justification-table"><thead><tr>';
    html = html + '<th scope="col" style="width:25%;">Edge cost</th>';
    html = html + '<th scope="col" style="text-align:left; width:25%" colspan=2>MacCartney relation</th>';
    html = html + '<th scope="col" style="text-align:left;">Fact</th></tr></thead>';
    html = html +'<tbody>';
    for (var i = 0; i < elements.length; ++i) {
      html = html + '<tr>';
      html = html +'<td>' + elements[i].cost + '</td>';
      html = html +'<td><b>' + getMacCartneyRelation(elements[i].incomingRelation) + '</b></td>';
      html = html +'<td style="text-align:left;"><i>' + getMacCartneyGloss(elements[i].incomingRelation) + '</i></td>';
      html = html +'<td style="text-align:left;">' + elements[i].gloss + '</td>';
      html = html +'</tr>';
    }
    html = html + '</tbody></table></div></div>';
  }

  $('#justification-container').html(html);
}

function handleError(message) {
  $( "#q" ).prop('disabled', false);
  $( "#query-button").unbind( "click" );
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
  $("#truth-value").removeClass("truth-value-unknown");
  $("#truth-value").removeClass("truth-value-true");
  $("#truth-value").removeClass("truth-value-false");
  $("#truth-value").addClass("truth-value-error")
  $("#truth-value").html('ERROR <div style="color: black; font-size: 12pt">(' + message + ')<div>');
}

function querySuccess(query) {
  return function(response) {
    $( "#q" ).prop('disabled', false);
    $( "#query-button").unbind( "click" );
    $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
    if (response.success) {
      displayTruth(response.bestResponseSource, response.isTrue);
      loadJustification(response.bestJustification, response.isTrue, response.bestResponseSource != "none", query);
    } else {
      handleError(response.errorMessage);
    }
  }
}


$(document).ready(function(){
  jQuery.support.cors = true;

  // Justification
  $("#justification-toggle-row").hide();

  // Query submit
  $( "#form-query" ).submit(function( event ) {
    // (don't actually submit anything)
    event.preventDefault();
    // (create a default if not input was given)
    if ( $( '#q' ).val().trim() == '') { $( '#q' ).val('cats have tails'); }
    // (start loading icon)
    $( '#truth-value' ).html('<img src="loading.gif" style="height:75px; margin-top:-35px;"/>');
    // (submission data)
    target = $(this).attr('action');
    getData = $(this).serialize();
    value = $( "#q" ).val();
    // (disable query button
    $( "#q" ).prop('disabled', true);
    $( "#query-button").unbind( "click" );
    // (ajax request)
    $.ajax({
      url: target,
      data: getData,
      dataType: 'json',
      success: querySuccess( value )
    });
  });

  // Query button
  $( "#query-button" ).mousedown(function(event) {
    $( '#query-button' ).css('background', 'darkgray');
  });
  $( document ).mouseup(function(event) {
    $( '#query-button' ).css('background', '');
  });
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });

  $( "#justification-toggle" ).click(function(event) {
    event.preventDefault();
    $("#justification-container").collapse('toggle');
    if ($('#justification-toggle-icon').hasClass('glyphicon-collapse-down')) {
      $('#justification-toggle-icon').removeClass('glyphicon-collapse-down');
      $('#justification-toggle-icon').addClass('glyphicon-expand');
    } else {
      $('#justification-toggle-icon').removeClass('glyphicon-expand');
      $('#justification-toggle-icon').addClass('glyphicon-collapse-down');
    }
  })
});