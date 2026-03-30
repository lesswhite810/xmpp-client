/**
 * JaCoCo XML Coverage Parser for com.example.xmpp.mechanism package.
 *
 * Strategy: Since JaCoCo XML groups <line> entries under <sourcefile> sections
 * (not <class> sections), and sourcefile sections are alphabetically ordered at
 * the package level (NOT in class-definition order), we must match sourcefile
 * names to our target classes and parse each sourcefile section independently.
 *
 * Key insight: <sourcefile> elements are siblings of <class> elements within
 * the <package> element, not children. Their order in the XML is alphabetical
 * by sourcefile name, completely independent of class definition order.
 *
 * This parser searches the package for each target sourcefile by name and
 * extracts LINE/BRANCH counters and missed lines from that sourcefile section.
 *
 * IMPORTANT: This parser applies the same excludes as pom.xml jacoco:check
 * configuration. Files matching the excludes patterns are filtered out so
 * the reported coverage matches what mvn verify sees.
 */
var fs = require('fs');
var xml = fs.readFileSync('target/site/jacoco/jacoco.xml', 'utf8');

// JaCoCo exclude patterns from pom.xml - these files are excluded from coverage
// The ** glob matches any path, so **/ScramMechanism.java matches ScramMechanism.java
// in any package. Since sourcefile names are just filenames (no path), we match
// the pattern's basename against the sourcefile name.
var excludes = [
  '**/config/**',
  '**/event/**',
  '**/exception/**',
  '**/example/**',
  '**/logic/**',
  '**/net/**',
  '**/protocol/**',
  '**/util/**',
  '**/ScramMechanism.java',
  '**/SaslNegotiator.java'
];

/**
 * Convert a JaCoCo glob pattern to a regex.
 * ** matches any characters including path separators
 * * matches any characters except path separators
 */
function globToRegex(pattern) {
  var regexStr = pattern
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')  // Escape special regex chars except * and **
    .replace(/\*\*/g, '.*')                 // ** -> .* (matches any chars including /)
    .replace(/\*/g, '[^/]*');               // * -> [^/]* (matches any chars except /)
  return new RegExp('^' + regexStr + '$');
}

// Check if a sourcefile name matches any exclude pattern.
// Handles JaCoCo glob patterns: ** matches any path, * matches within a path component.
// For filename patterns like {asterisk}{asterisk}/ScramMechanism.java, we match by basename only.
function isExcluded(sourcefileName) {
  // The sourcefile name in JaCoCo XML is just the filename (e.g., "ScramMechanism.java")
  // For a pattern like **/ScramMechanism.java, we check if the pattern ends with
  // /ScramMechanism.java or **/ScramMechanism.java, and if so, we match just the
  // basename part against ScramMechanism.java
  for (var i = 0; i < excludes.length; i++) {
    var pattern = excludes[i];
    // For pattern ending with a filename (not **), extract basename and match
    var basenamePattern = pattern.replace(/^\*\*/, '').replace(/^\/+/, '');
    if (basenamePattern.includes('.java') && !basenamePattern.includes('*')) {
      // This is a filename-only pattern like **/ScramMechanism.java
      // Extract the filename part and match against sourcefileName
      var filename = basenamePattern;
      if (sourcefileName === filename) {
        return true;
      }
    } else {
      // Check if full pattern matches (for package patterns like **/config/**)
      // Build full path to match against
      var fullPattern = 'com/example/xmpp/mechanism/' + sourcefileName;
      if (globToRegex(pattern).test(fullPattern)) {
        return true;
      }
    }
  }
  return false;
}

var pkgIdx = xml.indexOf('name="com/example/xmpp/mechanism"');
if (pkgIdx < 0) { console.log('Package not found'); process.exit(1); }
var pkgEnd = xml.indexOf('</package>', pkgIdx);
var pkg = xml.substring(pkgIdx, pkgEnd + 10);

// Target sourcefiles in the mechanism package
// Map: sourcefile name -> expected class path
var targets = [
  'ScramMechanism.java',
  'SaslNegotiator.java',
  'SaslMechanismFactory.java',
  'SaslMechanism.java',
  'PlainSaslMechanism.java',
  'ExternalSaslMechanism.java',
  'OAuthBearerSaslMechanism.java',
  'AnonymousSaslMechanism.java',
  'ScramSha1SaslMechanism.java',
  'ScramSha512SaslMechanism.java',
  'ScramSha256SaslMechanism.java',
  'SaslMechanismProvider.java'
];

// Collect non-excluded files and their coverage for package-level totals
var includedLineCounters = [];
var includedBranchCounters = [];

targets.forEach(function(sfName) {
  // Skip excluded files
  if (isExcluded(sfName)) {
    console.log('\n' + sfName + ': EXCLUDED (matches pom.xml exclude pattern)');
    return;
  }
  // Find this sourcefile's section within the package
  var sfStart = pkg.indexOf('<sourcefile name="' + sfName + '">');
  if (sfStart < 0) {
    console.log(sfName + ': NOT FOUND in JaCoCo report');
    return;
  }
  var sfEnd = pkg.indexOf('</sourcefile>', sfStart) + 12;
  var sfSegment = pkg.substring(sfStart, sfEnd);

  // Get LINE and BRANCH counters from this sourcefile section
  var lineC = null, branchC = null;
  var counterRe = /<counter type="(LINE|BRANCH)" missed="(\d+)" covered="(\d+)"/g;
  var m;
  while ((m = counterRe.exec(sfSegment)) !== null) {
    if (m[1] === 'LINE') lineC = { missed: parseInt(m[2]), covered: parseInt(m[3]) };
    else branchC = { missed: parseInt(m[2]), covered: parseInt(m[3]) };
  }

  // Collect counters for package-level aggregate (non-excluded files only)
  if (lineC) {
    includedLineCounters.push(lineC);
  }
  if (branchC) {
    includedBranchCounters.push(branchC);
  }

  // Collect missed lines (nr > 0 and mi > 0)
  var missedLines = [];
  var lineRegex = /<line nr="(\d+)" mi="(\d+)" ci="(\d+)" mb="(\d+)" cb="(\d+)"/g;
  while ((m = lineRegex.exec(sfSegment)) !== null) {
    if (parseInt(m[1]) > 0 && parseInt(m[2]) > 0) {
      missedLines.push({ line: parseInt(m[1]), mi: parseInt(m[2]), ci: parseInt(m[3]) });
    }
  }

  console.log('\n' + sfName);
  if (lineC) {
    var lt = lineC.missed + lineC.covered;
    var pct = lt > 0 ? (lineC.covered * 100 / lt).toFixed(1) : '0.0';
    console.log('  LINE: ' + lineC.covered + '/' + lt + ' (' + pct + '%) missed=' + lineC.missed);
  } else {
    console.log('  LINE: no data');
  }
  if (branchC) {
    var bt = branchC.missed + branchC.covered;
    var pct = bt > 0 ? (branchC.covered * 100 / bt).toFixed(1) : '0.0';
    console.log('  BRANCH: ' + branchC.covered + '/' + bt + ' (' + pct + '%) missed=' + branchC.missed);
  } else {
    console.log('  BRANCH: no data');
  }
  if (missedLines.length > 0) {
    missedLines.sort(function(a, b) { return a.line - b.line; });
    var lineNums = missedLines.map(function(l) { return l.line; });
    console.log('  Missed lines (' + missedLines.length + '): ' + lineNums.join(','));
  }
});

// Package-level aggregate - calculated from non-excluded files only
// This matches what mvn verify sees when it applies excludes
var totalLineMissed = 0, totalLineCovered = 0, totalBranchMissed = 0, totalBranchCovered = 0;
for (var i = 0; i < includedLineCounters.length; i++) {
  totalLineMissed += includedLineCounters[i].missed;
  totalLineCovered += includedLineCounters[i].covered;
}
for (var j = 0; j < includedBranchCounters.length; j++) {
  totalBranchMissed += includedBranchCounters[j].missed;
  totalBranchCovered += includedBranchCounters[j].covered;
}
console.log('\n--- Package com.example.xmpp.mechanism (aggregate, excludes applied) ---');
var totalLineTotal = totalLineMissed + totalLineCovered;
if (totalLineTotal > 0) {
  console.log('  LINE: ' + totalLineCovered + '/' + totalLineTotal + ' (' + (totalLineCovered * 100 / totalLineTotal).toFixed(1) + '%) missed=' + totalLineMissed);
} else {
  console.log('  LINE: no data');
}
var totalBranchTotal = totalBranchMissed + totalBranchCovered;
if (totalBranchTotal > 0) {
  console.log('  BRANCH: ' + totalBranchCovered + '/' + totalBranchTotal + ' (' + (totalBranchCovered * 100 / totalBranchTotal).toFixed(1) + '%) missed=' + totalBranchMissed);
} else {
  console.log('  BRANCH: no data');
}
