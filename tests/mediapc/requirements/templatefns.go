// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package templatefns contains functions that are made available in genreqsrc templates.
package templatefns

import (
  "strings"
  "text/template"
  "unicode"
)

// Funcs returns a mapping from names of template helper functions to the
// functions themselves.
func Funcs() template.FuncMap {
  // These function are made available in templates by calling their key values, e.g. {{SnakeCase "HelloWorld"}}.
  return template.FuncMap{
    // Case conversion functions.
    "LowerCase":      strings.ToLower,
    "UpperCase":      strings.ToUpper,
    "TitleCase":      titleCase,
    "SnakeCase":      snakeCase,
    "KebabCase":      kebabCase,
    "UpperCamelCase": upperCamelCase,
    "LowerCamelCase": lowerCamelCase,
    "SafeReqID":      safeReqID,
  }
}

// isDelimiter checks if a rune is some kind of whitespace, '_' or '-'.
// helper function
func isDelimiter(r rune) bool {
  return r == '-' || r == '_' || unicode.IsSpace(r)
}

func shouldWriteDelimiter(r, prev, next rune) bool {
  if isDelimiter(prev) {
    // Don't delimit if we just delimited
    return false
  }
  // Delimit before new uppercase letters and after acronyms
  caseDelimit := unicode.IsUpper(r) && (unicode.IsLower(prev) || unicode.IsLower(next))
  // Delimit after digits
  digitDelimit := !unicode.IsDigit(r) && unicode.IsDigit(prev)
  return isDelimiter(r) || caseDelimit || digitDelimit
}

// titleCase converts a string into Title Case.
func titleCase(s string) string {
  runes := []rune(s)
  var out strings.Builder
  for i, r := range runes {
    prev, next := ' ', ' '
    if i > 0 {
      prev = runes[i-1]
      if i+1 < len(runes) {
        next = runes[i+1]
      }
    }

    if shouldWriteDelimiter(r, prev, next) {
      out.WriteRune(' ')
    }

    if !isDelimiter(r) {
      // Output all non-delimiters unchanged
      out.WriteRune(r)
    }
  }
  return strings.Title(out.String())
}

// snakeCase converts a string into snake_case.
func snakeCase(s string) string {
  return strings.ReplaceAll(strings.ToLower(titleCase(s)), " ", "_")
}

// kebabCase converts a string into kebab-case.
func kebabCase(s string) string {
  return strings.ReplaceAll(strings.ToLower(titleCase(s)), " ", "-")
}

// upperCamelCase converts a string into UpperCamelCase.
func upperCamelCase(s string) string {
  return strings.ReplaceAll(titleCase(s), " ", "")
}

// lowerCamelCase converts a string into lowerCamelCase.
func lowerCamelCase(s string) string {
  if len(s) < 2 {
    return strings.ToLower(s)
  }
  runes := []rune(upperCamelCase(s))
  for i, r := range runes {
    // Lowercase leading acronyms
    if i > 1 && unicode.IsLower(r) {
      return strings.ToLower(string(runes[:i-1])) + string(runes[i-1:])
    }
  }
  return string(unicode.ToLower(runes[0])) + string(runes[1:])
}

// safeReqID converts a Media Performance Class (MPC) requirement id to a variable name safe string.
func safeReqID(s string) string {
  f := func(a, b, c string) string {
    return strings.Replace(a, b, c, -1)
  }
  return "r" + strings.ToLower(f(f(f(s, "/", "__"), ".", "_"), "-", "_"))
}
