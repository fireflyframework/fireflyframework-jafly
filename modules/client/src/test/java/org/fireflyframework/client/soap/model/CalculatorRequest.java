/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.client.soap.model;

import jakarta.xml.bind.annotation.*;

/**
 * JAXB model for calculator SOAP request.
 */
@XmlRootElement(name = "Add", namespace = "http://tempuri.org/")
@XmlAccessorType(XmlAccessType.FIELD)
public class CalculatorRequest {

    @XmlElement(name = "intA", namespace = "http://tempuri.org/")
    private int a;

    @XmlElement(name = "intB", namespace = "http://tempuri.org/")
    private int b;

    public CalculatorRequest() {
    }

    public CalculatorRequest(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }
}

