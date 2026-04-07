// Client-side weighted-random sponsor picker.
//
// Reads the JSON manifest the server injects into <script id="sponsors-data">,
// picks one entry weighted by `weight`, and renders <picture><img></picture>
// (plus optional additionalText) into the #sponsor placeholder. Selection is
// per pageview so it works behind a CDN that caches the HTML.
//
// CSP-friendly: external file under script-src 'self', no eval, no inline
// script, all DOM construction via createElement / textContent.
(function () {
  'use strict';

  var dataEl = document.getElementById('sponsors-data');
  var slot = document.querySelector('#sponsor .container');
  if (!dataEl || !slot) {
    return;
  }

  var sponsors;
  try {
    sponsors = JSON.parse(dataEl.textContent);
  } catch (e) {
    return;
  }
  if (!Array.isArray(sponsors) || sponsors.length === 0) {
    return;
  }

  var totalWeight = 0;
  for (var i = 0; i < sponsors.length; i++) {
    totalWeight += sponsors[i].weight;
  }
  if (totalWeight <= 0) {
    return;
  }

  var roll = Math.floor(Math.random() * totalWeight);
  var picked = sponsors[sponsors.length - 1];
  for (var j = 0; j < sponsors.length; j++) {
    roll -= sponsors[j].weight;
    if (roll < 0) {
      picked = sponsors[j];
      break;
    }
  }

  if (!picked || !picked.images || picked.images.length === 0) {
    return;
  }

  var anchor = document.createElement('a');
  anchor.href = picked.link;
  anchor.rel = 'noopener sponsored';
  anchor.target = '_blank';

  var picture = document.createElement('picture');
  for (var k = 0; k < picked.images.length; k++) {
    var img = picked.images[k];
    if (!img || !img.src) {
      continue;
    }
    if (img.media) {
      var source = document.createElement('source');
      source.media = img.media;
      source.srcset = '/assets/sponsors/' + img.src;
      if (img.type) {
        source.type = img.type;
      }
      picture.appendChild(source);
    }
  }
  var fallback = document.createElement('img');
  fallback.src = '/assets/sponsors/' + picked.images[0].src;
  fallback.alt = picked.name || '';
  picture.appendChild(fallback);
  anchor.appendChild(picture);

  if (picked.additionalText) {
    var note = document.createElement('p');
    note.className = 'additonalText';
    note.textContent = picked.additionalText;
    anchor.appendChild(note);
  }

  slot.appendChild(anchor);
})();
