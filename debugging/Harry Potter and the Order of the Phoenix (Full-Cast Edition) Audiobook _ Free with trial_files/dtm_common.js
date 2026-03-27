var DTM = {};

DTM.getVariable = function(a) {
	var satelliteVar = _satellite.getVar(a);
	if (satelliteVar === undefined || satelliteVar == null) {
		return ""; 
	}
	return satelliteVar;
};

DTM.appendToUrl = function(src, a) {
    return src + a;
};

DTM.safeIframeInsert = function(u) {
    var iframe = document.createElement('iframe');
    iframe.setAttribute("src", u);
    iframe.style.display = "none";
    iframe.frameBorder = 0;
    iframe.style.width = "1px";
    iframe.style.height = "1px";
    document.body.appendChild(iframe);
};

DTM.imageRequest = function(a) {
    var b = new Image(0, 0);
    b.src = a;
    return b;
};

DTM.addScript = function(src) {
  var s = document.createElement('script');
  s.setAttribute('src', src);
  s.type = "text/javascript";
  s.async = !0;
  document.body.appendChild(s);
}

DTM.noscriptImageRequest = function(a) {
    var n = document.createElement('noscript');
    var b = new Image(0, 0);
    b.src = a;
    n.appendChild(b);
    document.body.appendChild(n);
}

DTM.noscriptIframeInsert = function(u) {
    var n = document.createElement('noscript');

    var iframe = document.createElement('iframe');
    iframe.setAttribute("src", u);
    iframe.style.display = "none";
    iframe.frameBorder = 0;
    iframe.style.width = "1px";
    iframe.style.height = "1px";

    n.appendChild(iframe);

    document.body.appendChild(n);
}

DTM.loadScriptCallback = function(a, b, e) {
    var d = document.getElementsByTagName("script"),
        c;
    e = d[0];
    for (c = 0; c < d.length; c++)
        if (d[c].src === a && d[c].readyState && /loaded|complete/.test(d[c].readyState)) try {
            b()
        } catch (f) {
        } finally {
            return
        }
    d = document.createElement("script");
    d.type = "text/javascript";
    d.async = !0;
    d.src = a;
    d.onerror = function() {
        this.addEventListener && (this.readyState = "loaded")
    };
    d.onload = d.onreadystatechange = function() {
        if (!this.readyState || "complete" === this.readyState || "loaded" === this.readyState) {
            this.onload = this.onreadystatechange = null;
            this.addEventListener && (this.readyState = "loaded");
            try {
                b.call(this)
            } catch (f) {
            }
        }
    };
    e.parentNode.insertBefore(d, e)
};

DTM.comparePageGroups = function(a) {
	var pageGroup = DTM.getVariable("tm: pageGroup");
	if (pageGroup.toUpperCase() == a.toUpperCase()) {
		return true;
	}
	return false;
};

DTM.compareRealms = function(r) {
	var realm = DTM.getVariable("realm");	
	if (realm.toUpperCase() == r.toUpperCase()) {
		return true;
	}
	return false;
};

DTM.isCustomerAnon = function(customerType) {
    if (customerType == "ANON" || customerType == "REG_NO_BUY") {
        return true;
    }
}

DTM.isCustomerNonMember = function(customerType) {
    if (customerType == "FORMER_CREDIT_AL" || customerType == "FORMER_NON_CREDIT_AL" || customerType == "FORMER_TRIAL_AL" || customerType == "FORMER_GIFT_MEMBER" || customerType == "ALC" || customerType == "FREEBIE_BUY") {
        return true;
    }
}

DTM.isCustomerMember = function(customerType) {
    // Comment added 0
    if (customerType == "CREDIT_AL" || customerType == "NON_CREDIT_AL" || customerType == "TRIAL_AL" || customerType == "GIFT_MEMBER") {
        return true;
    }
}