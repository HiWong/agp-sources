
package com.android.repository.impl.sources.generated.v1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * DO NOT EDIT
 * This file was generated by xjc from repo-sites-common-1.xsd. Any changes will be lost upon recompilation of the schema.
 * See the schema file for instructions on running xjc.
 *
 *
 *                 A trivial implementation of siteType.
 *
 *
 * <p>Java class for genericSiteType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="genericSiteType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{http://schemas.android.com/repository/android/sites-common/1}siteType"&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "genericSiteType")
@SuppressWarnings({
    "override",
    "unchecked"
})
public class GenericSiteType
    extends SiteType
    implements com.android.repository.api.RemoteListSourceProvider.GenericSite
{


    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}