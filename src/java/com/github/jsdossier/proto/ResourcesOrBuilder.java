/*
 Copyright 2013-2015 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: dossier.proto

package com.github.jsdossier.proto;

public interface ResourcesOrBuilder extends
    // @@protoc_insertion_point(interface_extends:dossier.Resources)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>repeated string css = 1;</code>
   *
   * <pre>
   * Paths to stylesheets to link to in the generated page.
   * </pre>
   */
  com.google.protobuf.ProtocolStringList
      getCssList();
  /**
   * <code>repeated string css = 1;</code>
   *
   * <pre>
   * Paths to stylesheets to link to in the generated page.
   * </pre>
   */
  int getCssCount();
  /**
   * <code>repeated string css = 1;</code>
   *
   * <pre>
   * Paths to stylesheets to link to in the generated page.
   * </pre>
   */
  java.lang.String getCss(int index);
  /**
   * <code>repeated string css = 1;</code>
   *
   * <pre>
   * Paths to stylesheets to link to in the generated page.
   * </pre>
   */
  com.google.protobuf.ByteString
      getCssBytes(int index);

  /**
   * <code>repeated string head_script = 2;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page's head.
   * </pre>
   */
  com.google.protobuf.ProtocolStringList
      getHeadScriptList();
  /**
   * <code>repeated string head_script = 2;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page's head.
   * </pre>
   */
  int getHeadScriptCount();
  /**
   * <code>repeated string head_script = 2;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page's head.
   * </pre>
   */
  java.lang.String getHeadScript(int index);
  /**
   * <code>repeated string head_script = 2;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page's head.
   * </pre>
   */
  com.google.protobuf.ByteString
      getHeadScriptBytes(int index);

  /**
   * <code>repeated string tail_script = 3;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page after
   * the DOM has been defined.
   * </pre>
   */
  com.google.protobuf.ProtocolStringList
      getTailScriptList();
  /**
   * <code>repeated string tail_script = 3;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page after
   * the DOM has been defined.
   * </pre>
   */
  int getTailScriptCount();
  /**
   * <code>repeated string tail_script = 3;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page after
   * the DOM has been defined.
   * </pre>
   */
  java.lang.String getTailScript(int index);
  /**
   * <code>repeated string tail_script = 3;</code>
   *
   * <pre>
   * Paths to JavaScript files to include in the generated page after
   * the DOM has been defined.
   * </pre>
   */
  com.google.protobuf.ByteString
      getTailScriptBytes(int index);
}