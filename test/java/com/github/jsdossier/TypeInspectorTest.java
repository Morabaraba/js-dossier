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

package com.github.jsdossier;

import static com.google.common.truth.Truth.assertThat;

import com.github.jsdossier.TypeInspector.InstanceProperty;
import com.github.jsdossier.proto.BaseProperty;
import com.github.jsdossier.proto.Comment;
import com.github.jsdossier.proto.Function;
import com.github.jsdossier.proto.Function.Detail;
import com.github.jsdossier.proto.Property;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/**
 * Tests for {@link TypeInspector}.
 */
@RunWith(JUnit4.class)
public class TypeInspectorTest extends AbstractTypeInspectorTest {

  @Test
  public void getInstanceProperties_vanillaClass() {
    compile(
        "/** @constructor */",
        "function Person() {",
        "  this.name = 'Bob';",
        "  this.sleep = function() {};",
        "}",
        "Person.shouldIgnoreStaticProperty = function() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "Person.prototype.eat = function() {};");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_googDefinedClass() {
    compile(
        "var Person = goog.defineClass(null, {",
        "  constructor: function() {",
        "    this.name = 'Bob';",
        "    this.sleep = function() {};",
        "  },",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {number} */age: 123,",
        "  eat: function() {}",
        "});");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_vanillaInterface() {
    compile(
        "/** @interface */",
        "function Person() {}",
        "/** @type {number} */",
        "Person.prototype.age;",
        "/** @type {string} */",
        "Person.prototype.name;",
        "Person.prototype.sleep = function() {}",
        "Person.prototype.eat = function(food) {};");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_googDefinedInterface() {
    compile(
        "/** @interface */",
        "var Person = goog.defineClass(null, {",
        "  statics: { shouldIgnoreStaticProperty: function() {} },",
        "  /** @type {string} */name: 'Bob',",
        "  /** @type {number} */age: 123,",
        "  eat: function() {},",
        "  sleep: function() {}",
        "});");

    NominalType person = typeRegistry.getNominalType("Person");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(person.getJsType());

    assertThat(properties.keySet()).containsExactly("name", "age", "eat", "sleep");

    InstanceProperty name = properties.get("name");
    assertInstanceProperty(name).isNamed("name");
    assertInstanceProperty(name).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(name).isDefinedOn(person.getJsType());

    InstanceProperty age = properties.get("age");
    assertInstanceProperty(age).isNamed("age");
    assertInstanceProperty(age).hasType(typeRegistry.getJsType("number"));
    assertInstanceProperty(age).isDefinedOn(person.getJsType());

    InstanceProperty sleep = properties.get("sleep");
    assertInstanceProperty(sleep).isNamed("sleep");
    assertInstanceProperty(sleep).isInstanceMethod(person.getJsType());
    assertInstanceProperty(sleep).isDefinedOn(person.getJsType());

    InstanceProperty eat = properties.get("eat");
    assertInstanceProperty(eat).isNamed("eat");
    assertInstanceProperty(eat).isInstanceMethod(person.getJsType());
    assertInstanceProperty(eat).isDefinedOn(person.getJsType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromSuperClass() {
    compile(
        "/** @constructor */",
        "function Person() {}",
        "/** @type {number} */Person.prototype.age;",
        "/** @type {string} */Person.prototype.name;",
        "",
        "/** @constructor @extends {Person} */",
        "function SuperHero() {}",
        "goog.inherits(SuperHero, Person);",
        "/** @type {string} */SuperHero.prototype.power;");

    NominalType hero = typeRegistry.getNominalType("SuperHero");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(hero.getJsType());

    assertThat(properties.keySet()).containsExactly("power");

    InstanceProperty power = properties.get("power");
    assertInstanceProperty(power).isNamed("power");
    assertInstanceProperty(power).hasType(typeRegistry.getJsType("string"));
    assertInstanceProperty(power).isDefinedOn(hero.getJsType());
  }

  @Test
  public void getInstanceProperties_doesNotIncludePropertiesFromParentInterface() {
    compile(
        "/** @interface */",
        "function Person() {}",
        "Person.prototype.eat = function() {};",
        "Person.prototype.sleep = function() {};",
        "",
        "/** @interface @extends {Person} */",
        "function Athlete() {}",
        "Athlete.prototype.run = function() {}");

    NominalType athlete = typeRegistry.getNominalType("Athlete");
    Map<String, InstanceProperty> properties =
        typeInspector.getInstanceProperties(athlete.getJsType());

    assertThat(properties.keySet()).containsExactly("run");

    InstanceProperty run = properties.get("run");
    assertInstanceProperty(run).isNamed("run");
    assertInstanceProperty(run).isInstanceMethod(athlete.getJsType());
    assertInstanceProperty(run).isDefinedOn(athlete.getJsType());
  }
}