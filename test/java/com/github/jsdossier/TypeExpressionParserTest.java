/*
Copyright 2013-2016 Jason Leyba

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

package com.github.jsdossier;

import static com.github.jsdossier.TypeExpressions.ANY_TYPE;
import static com.github.jsdossier.TypeExpressions.NULL_TYPE;
import static com.github.jsdossier.TypeExpressions.UNKNOWN_TYPE;
import static com.github.jsdossier.TypeExpressions.VOID_TYPE;
import static com.github.jsdossier.testing.CompilerUtil.createSourceFile;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.html.types.testing.HtmlConversions.newSafeUrlProtoForTest;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.github.jsdossier.annotations.Input;
import com.github.jsdossier.jscomp.NominalType;
import com.github.jsdossier.jscomp.TypeRegistry;
import com.github.jsdossier.proto.FunctionType;
import com.github.jsdossier.proto.NamedType;
import com.github.jsdossier.proto.RecordType;
import com.github.jsdossier.proto.TypeExpression;
import com.github.jsdossier.proto.TypeLink;
import com.github.jsdossier.proto.UnionType;
import com.github.jsdossier.testing.CompilerUtil;
import com.github.jsdossier.testing.GuiceRule;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.Property;
import java.nio.file.FileSystem;
import java.util.Arrays;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TypeExpressionParser}. */
@RunWith(JUnit4.class)
public class TypeExpressionParserTest {

  @Rule
  public GuiceRule guice =
      GuiceRule.builder(this)
          .setOutputDir("out")
          .setSourcePrefix("source")
          .setModulePrefix("source/modules")
          .setModules("one.js", "two.js", "three.js")
          .build();

  @Inject @Input private FileSystem fs;
  @Inject private CompilerUtil util;
  @Inject private TypeRegistry typeRegistry;
  @Inject private LinkFactoryBuilder linkFactoryBuilder;
  @Inject private TypeExpressionParserFactory parserFactory;

  @Test
  public void parseTypeDefinition() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {{name: string, age: number}} */var Person;");

    NominalType type = typeRegistry.getType("Person");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSType jsType = util.evaluate(type.getJsDoc().getInfo().getTypedefType());

    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setRecordType(
                    RecordType.newBuilder()
                        .addEntry(
                            RecordType.Entry.newBuilder().setKey("age").setValue(numberType()))
                        .addEntry(
                            RecordType.Entry.newBuilder().setKey("name").setValue(stringType())))
                .build());
  }

  @Test
  public void parseConstructorFunctionReference() {
    util.compile(
        fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(new: Person)} a A person constructor.",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setFunctionType(
                    FunctionType.newBuilder()
                        .setIsConstructor(true)
                        .setInstanceType(namedTypeExpression("Person", "Person.html")))
                .build());
  }

  @Test
  public void parseFunctionTypeExpressionWithNoReturnType() {
    util.compile(
        fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(this: Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setFunctionType(
                    FunctionType.newBuilder()
                        .setInstanceType(namedTypeExpression("Person", "Person.html"))
                        .setReturnType(TypeExpression.newBuilder().setUnknownType(true)))
                .build());
  }

  @Test
  public void parseFunctionTypeExpressionWithReturnType() {
    util.compile(
        fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(): Person} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setFunctionType(
                    FunctionType.newBuilder()
                        .setReturnType(
                            unionType(namedTypeExpression("Person", "Person.html"), NULL_TYPE)))
                .build());
  }

  @Test
  public void parseFunctionTypeExpressionWithVarArgs() {
    util.compile(
        fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(...!Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression expression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(expression);

    TypeExpression typeExpression = parser.parse(jsType);
    assertThat(typeExpression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setFunctionType(
                    FunctionType.newBuilder()
                        .addParameter(
                            TypeExpression.newBuilder()
                                .setIsVarargs(true)
                                .setNamedType(namedType("Person", "Person.html")))
                        .setReturnType(UNKNOWN_TYPE))
                .build());
  }

  @Test
  public void parseFunctionTypeExpressionWithVarArgs_withContext() {
    util.compile(
        fs.getPath("foo.js"),
        "class Person {}",
        "/**",
        " * @param {function(this: Person, ...!Person)} a .",
        " * @constructor",
        " */",
        "function Greeter(a) {}");

    NominalType type = typeRegistry.getType("Greeter");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));
    JSTypeExpression jsExpression = type.getJsDoc().getParameter("a").getType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setFunctionType(
                    FunctionType.newBuilder()
                        .setInstanceType(namedTypeExpression("Person", "Person.html"))
                        .addParameter(
                            namedTypeExpression("Person", "Person.html")
                                .toBuilder()
                                .setIsVarargs(true))
                        .setReturnType(TypeExpression.newBuilder().setUnknownType(true)))
                .build());
  }

  @Test
  public void moduleContextWillHideGlobalTypeNames() {
    util.compile(
        createSourceFile(fs.getPath("source/global.js"), "class Person {}"),
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "export class Person {}",
            "/**",
            " * @param {!Person} a A person.",
            " * @constructor",
            " */",
            "export function Greeter(a) {}"));

    NominalType type = typeRegistry.getType("module$source$modules$one.Greeter");
    TypeExpressionParser parser =
        parserFactory.create(linkFactoryBuilder.create(type).withTypeContext(type));

    JSType jsType = util.evaluate(type.getJsDoc().getParameter("a").getType());
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setNamedType(namedType("Person", "one.Person", "one_exports_Person.html"))
                .build());
  }

  @Test
  public void redundantNonNullQualifierOnPrimitiveIsIncluded() {
    util.compile(fs.getPath("foo.js"), "/** @typedef {!string} */var Name;");

    NominalType type = typeRegistry.getType("Name");
    TypeExpressionParser parser = parserFactory.create(linkFactoryBuilder.create(type));

    JSTypeExpression jsExpression = type.getJsDoc().getInfo().getTypedefType();
    JSType jsType = util.evaluate(jsExpression);
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression).isEqualTo(stringType());
  }

  @Test
  public void parseExpressionWithTemplatizedType() {
    util.compile(
        createSourceFile(
            fs.getPath("source/global.js"),
            "/** @template T */",
            "class Container {}",
            "class Person {",
            "  /** @return {!Container<string>} . */",
            "  name() { return new Container; }",
            "}"));

    NominalType type = typeRegistry.getType("Person");
    JSDocInfo info =
        type.getType().toMaybeFunctionType().getPrototype().getOwnPropertyJSDocInfo("name");
    JSTypeExpression jsExpression = info.getReturnType();
    JSType jsType = util.evaluate(jsExpression);

    TypeExpressionParser parser =
        parserFactory.create(linkFactoryBuilder.create(type).withTypeContext(type));
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setNamedType(
                    namedType("Container", "Container.html")
                        .toBuilder()
                        .addTemplateType(stringType()))
                .build());
  }

  @Test
  public void parseExpressionWithTemplatizedTypeFromAnotherModule() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"), "/** @template T */", "export class Container {}"),
        createSourceFile(
            fs.getPath("source/modules/two.js"),
            "import {Container} from './one';",
            "export class Person {",
            "  /** @return {!Container<string>} . */",
            "  name() { return new Container; }",
            "}"));

    NominalType type = typeRegistry.getType("module$source$modules$two.Person");
    JSDocInfo info =
        type.getType().toMaybeFunctionType().getPrototype().getOwnPropertyJSDocInfo("name");
    JSTypeExpression jsExpression = info.getReturnType();
    JSType jsType = util.evaluate(jsExpression);

    TypeExpressionParser parser =
        parserFactory.create(linkFactoryBuilder.create(type).withTypeContext(type));
    TypeExpression expression = parser.parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setNamedType(
                    namedType("Container", "one.Container", "one_exports_Container.html")
                        .toBuilder()
                        .addTemplateType(stringType()))
                .build());
  }

  @Test
  public void parseExpression_unknownType() {
    TypeExpression expression = compileExpression("?");
    assertThat(expression).isEqualTo(UNKNOWN_TYPE);
  }

  @Test
  public void parseExpression_anyType() {
    TypeExpression expression = compileExpression("*");
    assertThat(expression).isEqualTo(ANY_TYPE);
  }

  @Test
  public void parsesExpression_primitiveType() {
    TypeExpression expression = compileExpression("string");
    assertThat(expression).isEqualTo(stringType());
  }

  @Test
  public void parsesExpression_nullablePrimitiveType1() {
    TypeExpression expression = compileExpression("?string");
    assertThat(expression).isEqualTo(unionType(stringType(), NULL_TYPE));
  }

  @Test
  public void parsesExpression_nullablePrimitiveType2() {
    TypeExpression expression = compileExpression("string?");
    assertThat(expression).isEqualTo(unionType(stringType(), NULL_TYPE));
  }

  @Test
  public void parseExpression_primitiveUnionType() {
    TypeExpression expression = compileExpression("string|number");
    assertThat(expression).isEqualTo(unionType(stringType(), numberType()));
  }

  @Test
  public void parseExpression_nullablePrimitiveUnionType() {
    TypeExpression expression = compileExpression("?(string|number)");
    assertThat(expression).isEqualTo(unionType(stringType(), numberType(), NULL_TYPE));
  }

  @Test
  public void parseExpression_unionWithNullableComponent() {
    TypeExpression expression = compileExpression("(?string|number)");
    assertThat(expression).isEqualTo(unionType(stringType(), numberType(), NULL_TYPE));
  }

  @Test
  public void parseExpression_unionAnyTypeIsAnyType() {
    TypeExpression expression = compileExpression("(string|*)");
    assertThat(expression).isEqualTo(ANY_TYPE);
  }

  @Test
  public void parseExpression_unionWithUnknownTypeIsUnknownType() {
    TypeExpression expression = compileExpression("(string|?)");
    assertThat(expression).isEqualTo(UNKNOWN_TYPE);
  }

  @Test
  public void parseExpression_unionOfNullAndUndefined() {
    TypeExpression expression = compileExpression("(null|undefined)");
    assertThat(expression).isEqualTo(unionType(NULL_TYPE, VOID_TYPE));
  }

  @Test
  public void parseExpression_recordTypeWithNullablePrimitive() {
    TypeExpression expression = compileExpression("{age: ?number}");
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setRecordType(
                    RecordType.newBuilder()
                        .addEntry(
                            RecordType.Entry.newBuilder()
                                .setKey("age")
                                .setValue(unionType(numberType(), NULL_TYPE))))
                .build());
  }

  @Test
  public void parseExpression_externalEnumReference() {
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            createSourceFile(
                fs.getPath("externs.js"), "/** @enum {string} */", "var Data = {ONE: 'one'};"));

    ImmutableList<SourceFile> sources =
        ImmutableList.of(
            createSourceFile(
                fs.getPath("one.js"),
                "/**",
                " * @param {Data} x .",
                " * @constructor",
                " */",
                "function Widget(x) {}"));
    util.compile(externs, sources);

    NominalType type = typeRegistry.getType("Widget");
    JSTypeExpression jsTypeExpression = type.getJsDoc().getParameter("x").getType();
    JSType jsType = util.evaluate(jsTypeExpression);
    TypeExpression expression = parserFactory.create(linkFactoryBuilder.create(type)).parse(jsType);
    assertThat(expression)
        .isEqualTo(
            TypeExpression.newBuilder()
                .setNamedType(NamedType.newBuilder().setName("Data"))
                .build());
  }

  @Test
  public void parseExpression_typeIsReferenceToNullableExtern() {
    util.compile(
        createSourceFile(
            fs.getPath("source/modules/one.js"),
            "var stream = require('stream');",
            "/** @constructor */",
            "function Writer() {}",
            "/** @type {stream.Stream} */",
            "Writer.prototype.stream = null;",
            "exports.Writer = Writer"));

    NominalType type = typeRegistry.getType("module$exports$module$source$modules$one.Writer");

    Property property = type.getType().toMaybeFunctionType().getInstanceType().getSlot("stream");
    TypeExpression expression =
        parserFactory.create(linkFactoryBuilder.create(type)).parse(property.getType());
    assertThat(expression).isEqualTo(unionType(namedTypeExpression("stream.Stream"), NULL_TYPE));
  }

  private TypeExpression compileExpression(String expressionText) {
    util.compile(
        createSourceFile(
            fs.getPath("one.js"),
            "/**",
            " * @param {" + expressionText + "} x .",
            " * @constructor",
            " */",
            "function Widget(x) {}"));
    NominalType type = typeRegistry.getType("Widget");
    JSTypeExpression expression = type.getJsDoc().getParameter("x").getType();
    JSType jsType = util.evaluate(expression);
    return parserFactory.create(linkFactoryBuilder.create(type)).parse(jsType);
  }

  private static TypeExpression numberType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder().setExtern(true).setName("number"))
        .build();
  }

  private static TypeExpression stringType() {
    return TypeExpression.newBuilder()
        .setNamedType(NamedType.newBuilder().setExtern(true).setName("string"))
        .build();
  }

  private static TypeExpression unionType(TypeExpression... expressions) {
    checkArgument(expressions.length > 0);
    return TypeExpression.newBuilder()
        .setUnionType(UnionType.newBuilder().addAllType(Arrays.asList(expressions)))
        .build();
  }

  private static TypeExpression namedTypeExpression(String text) {
    return TypeExpression.newBuilder().setNamedType(namedType(text)).build();
  }

  private static TypeExpression namedTypeExpression(String text, String href) {
    return TypeExpression.newBuilder().setNamedType(namedType(text, href)).build();
  }

  private static NamedType namedType(String name) {
    return NamedType.newBuilder().setName(name).build();
  }

  private static NamedType namedType(String name, String href) {
    return NamedType.newBuilder()
        .setName(name)
        .setLink(TypeLink.newBuilder().setHref(newSafeUrlProtoForTest(href)))
        .build();
  }

  private static NamedType namedType(String name, String qualifiedName, String href) {
    return NamedType.newBuilder()
        .setName(name)
        .setQualifiedName(qualifiedName)
        .setLink(TypeLink.newBuilder().setHref(newSafeUrlProtoForTest(href)))
        .build();
  }
}
