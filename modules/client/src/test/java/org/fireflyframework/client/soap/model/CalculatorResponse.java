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
 * JAXB model for calculator SOAP response.
 */
@XmlRootElement(name = "AddResponse", namespace = "http://tempuri.org/")
@XmlAccessorType(XmlAccessType.FIELD)
public class CalculatorResponse {

    @XmlElement(name = "AddResult", namespace = "http://tempuri.org/")
    private int result;

    public CalculatorResponse() {
    }

    public CalculatorResponse(int result) {
        this.result = result;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}

