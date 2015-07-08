/*
 * Copyright 2006-2008 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webcohesion.enunciate.modules.jaxws.model;

import com.webcohesion.enunciate.EnunciateException;
import com.webcohesion.enunciate.facets.Facet;
import com.webcohesion.enunciate.facets.HasFacets;
import com.webcohesion.enunciate.javac.decorations.TypeMirrorDecorator;
import com.webcohesion.enunciate.javac.decorations.element.DecoratedExecutableElement;
import com.webcohesion.enunciate.metadata.ClientName;
import com.webcohesion.enunciate.modules.jaxb.model.util.MapType;
import com.webcohesion.enunciate.modules.jaxws.EnunciateJaxwsContext;

import javax.jws.Oneway;
import javax.jws.soap.SOAPBinding;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * A method invoked on a web service.
 *
 * @author Ryan Heaton
 */
public class WebMethod extends DecoratedExecutableElement implements Comparable<WebMethod>, HasFacets {

  private final javax.jws.WebMethod annotation;
  private final boolean oneWay;
  private final EndpointInterface endpointInterface;
  private final WebResult webResult;
  private final Collection<WebParam> webParams;
  private final Collection<WebFault> webFaults;
  private final Collection<WebMessage> messages;
  private final RequestWrapper requestWrapper;
  private final ResponseWrapper responseWrapper;
  private final Set<Facet> facets = new TreeSet<Facet>();
  private final EnunciateJaxwsContext context;

  public WebMethod(ExecutableElement delegate, EndpointInterface endpointInterface, EnunciateJaxwsContext context) {
    super(delegate, context.getContext().getProcessingEnvironment());
    this.context = context;

    this.annotation = getAnnotation(javax.jws.WebMethod.class);
    this.oneWay = getAnnotation(Oneway.class) != null;
    this.endpointInterface = endpointInterface;
    this.webResult = new WebResult(getReturnType(), this, context);

    List<? extends VariableElement> parameters = getParameters();
    Collection<WebParam> webParameters = new ArrayList<WebParam>(parameters.size());
    int parameterIndex = 0;
    for (VariableElement parameter : parameters) {
      webParameters.add(new WebParam(parameter, this, parameterIndex++, context));
    }
    this.webParams = webParameters;

    Collection<WebFault> webFaults = new ArrayList<WebFault>();
    for (TypeMirror referenceType : getThrownTypes()) {
      if (!(referenceType instanceof DeclaredType)) {
        throw new EnunciateException("Method " + getSimpleName() + " of " + endpointInterface.getQualifiedName() + ": Thrown type must be a declared type.");
      }

      TypeElement declaration = (TypeElement) ((DeclaredType) referenceType).asElement();

      if (declaration == null) {
        throw new EnunciateException("Method " + getSimpleName() + " of " + endpointInterface.getQualifiedName() + ": unknown declaration for " + referenceType);
      }

      webFaults.add(new WebFault(declaration, context));
    }
    this.webFaults = webFaults;

    Collection<WebMessage> messages = new ArrayList<WebMessage>();
    SOAPBinding.Style bindingStyle = getSoapBindingStyle();

    //first add all the headers.
    for (WebParam webParam : webParameters) {
      if (webParam.isHeader()) {
        messages.add(webParam);
      }
    }

    if (webResult.isHeader()) {
      messages.add(webResult);
    }

    RequestWrapper requestWrapper = null;
    ResponseWrapper responseWrapper = null;
    if (bindingStyle == SOAPBinding.Style.DOCUMENT) {
      SOAPBinding.ParameterStyle parameterStyle = getSoapParameterStyle();
      if (parameterStyle == SOAPBinding.ParameterStyle.WRAPPED) {
        requestWrapper = new RequestWrapper(this);
        messages.add(requestWrapper);
        if (!isOneWay()) {
          responseWrapper = new ResponseWrapper(this);
          messages.add(responseWrapper);
        }
        messages.addAll(webFaults);
      }
      else {
        for (WebParam webParam : webParameters) {
          if (!webParam.isHeader()) {
            messages.add(webParam);
          }
        }
        if (!isOneWay() && getReturnType().getKind() != TypeKind.VOID && !webResult.isHeader()) {
          messages.add(webResult);
        }

        messages.addAll(webFaults);
      }
    }
    else {
      messages.add(new RPCInputMessage(this));
      messages.add(new RPCOutputMessage(this));
      messages.addAll(webFaults);
    }

    this.messages = messages;
    this.requestWrapper = requestWrapper;
    this.responseWrapper = responseWrapper;
    this.facets.addAll(Facet.gatherFacets(delegate));
    this.facets.addAll(endpointInterface.getFacets());
  }

  /**
   * The simple name for client-side code generation.
   *
   * @return The simple name for client-side code generation.
   */
  public String getClientSimpleName() {
    String clientSimpleName = getSimpleName().toString();
    ClientName clientName = getAnnotation(ClientName.class);
    if (clientName != null) {
      clientSimpleName = clientName.value();
    }
    return clientSimpleName;
  }


  @Override
  public TypeMirror getReturnType() {
    TypeMirror type = super.getReturnType();
    MapType mapType = MapType.findMapType(type, this.context.getJaxbContext());
    if (mapType != null) {
      type = mapType;
    }
    return TypeMirrorDecorator.decorate(type, this.env);
  }

  /**
   * The web result of this web method.
   *
   * @return The web result of this web method.
   */
  public WebResult getWebResult() {
    return this.webResult;
  }

  /**
   * The list of web parameters for this method.
   *
   * @return The list of web parameters for this method.
   */
  public Collection<WebParam> getWebParameters() {
    return this.webParams;
  }

  /**
   * The list of web faults thrown by this method.
   *
   * @return The list of web faults thrown by this method.
   */
  public Collection<WebFault> getWebFaults() {
    return this.webFaults;
  }

  /**
   * The messages of this web method.
   *
   * @return The messages of this web method.
   */
  public Collection<WebMessage> getMessages() {
    return this.messages;
  }

  /**
   * The request wrapper.
   *
   * @return The request wrapper, or null if none.
   */
  public RequestWrapper getRequestWrapper() {
    return requestWrapper;
  }

  /**
   * The response wrapper, or null if none.
   *
   * @return The response wrapper, or null if none.
   */
  public ResponseWrapper getResponseWrapper() {
    return responseWrapper;
  }

  /**
   * A set of the reference namespace for this method.
   *
   * @return A set of the reference namespace for this method.
   */
  public Set<String> getReferencedNamespaces() {
    HashSet<String> namespaces = new HashSet<String>();

    Collection<WebMessage> messages = getMessages();
    for (WebMessage message : messages) {
      for (WebMessagePart part : message.getParts()) {
        namespaces.add(part.getParticleQName().getNamespaceURI());
      }
    }
    
    return namespaces;
  }

  /**
   * The operation name of this web method.
   *
   * @return The operation name of this web method.
   */
  public String getOperationName() {
    String operationName = getSimpleName().toString();

    if ((annotation != null) && (!"".equals(annotation.operationName()))) {
      return annotation.operationName();
    }

    return operationName;
  }

  /**
   * The action of this web method.
   *
   * @return The action of this web method.
   */
  public String getAction() {
    String action = "";

    if (annotation != null) {
      action = annotation.action();
    }

    return action;
  }

  /**
   * Whether this web method is one-way.
   *
   * @return Whether this web method is one-way.
   */
  public boolean isOneWay() {
    return oneWay;
  }

  /**
   * The SOAP binding style of this web method.
   *
   * @return The SOAP binding style of this web method.
   */
  public SOAPBinding.Style getSoapBindingStyle() {
    SOAPBinding.Style style = getDeclaringEndpointInterface().getSoapBindingStyle();
    SOAPBinding bindingInfo = getAnnotation(SOAPBinding.class);

    if (bindingInfo != null) {
      style = bindingInfo.style();
    }

    return style;
  }

  /**
   * The declaring web service for this web method.
   *
   * @return The declaring web service for this web method.
   */
  public EndpointInterface getDeclaringEndpointInterface() {
    return endpointInterface;
  }

  /**
   * The SOAP binding use of this web method.
   *
   * @return The SOAP binding use of this web method.
   */
  public SOAPBinding.Use getSoapUse() {
    SOAPBinding.Use use = getDeclaringEndpointInterface().getSoapUse();
    SOAPBinding bindingInfo = getAnnotation(SOAPBinding.class);

    if (bindingInfo != null) {
      use = bindingInfo.use();
    }

    return use;
  }

  /**
   * The SOAP parameter style of this web method.
   *
   * @return The SOAP parameter style of this web method.
   */
  public SOAPBinding.ParameterStyle getSoapParameterStyle() {
    SOAPBinding.ParameterStyle style = getDeclaringEndpointInterface().getSoapParameterStyle();
    SOAPBinding bindingInfo = getAnnotation(SOAPBinding.class);

    if (bindingInfo != null) {
      style = bindingInfo.parameterStyle();
    }

    return style;
  }

  /**
   * Determine whether this web method is doc/lit wrapped.
   *
   * @return Whether this web method is doc/lit wrapped.
   */
  public boolean isDocLitWrapped() {
    return getSoapBindingStyle() == SOAPBinding.Style.DOCUMENT &&
      getSoapUse() == SOAPBinding.Use.LITERAL &&
      getSoapParameterStyle() == SOAPBinding.ParameterStyle.WRAPPED;
  }

  /**
   * Web methods must be unique by name.  (JSR 181: 3.1.1)
   *
   * @param webMethod The web method to compare this to.
   * @return The comparison.
   */
  public int compareTo(WebMethod webMethod) {
    return getOperationName().compareTo(webMethod.getOperationName());
  }

  /**
   * The facets here applicable.
   *
   * @return The facets here applicable.
   */
  public Set<Facet> getFacets() {
    return facets;
  }

}