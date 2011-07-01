(function($) {
	// simple d3-like data binding solution
	// returns set of updated nodes extended by the methods enter() and exit()
	$.fn.bindData = function(key, data, matchKeyFunc) {
			var keyedData = {};
			if (matchKeyFunc) {
				$.each(data, function(index, value) { 
					keyedData[matchKeyFunc(data[index])] = data[index]; 
				});
			}
			
			var enterData;
			var exitSet = $();
			
			var maxIndex = -1;
			var usedKeys = {};
			
			return $.extend(this.filter(function(i) {
					maxIndex = i;
					var self = $(this);
					
					var matched = false;
					if (matchKeyFunc) {
						var existingData = self.data(key);
						if (existingData !== undefined) {
							var dataKey = matchKeyFunc(existingData);
							if (keyedData[dataKey] !== undefined) {
								self.data(key, keyedData[dataKey]);
								usedKeys[dataKey] = true;
								matched = true;
							}
						}
					} else {
						if (i < data.length) {
							self.data(key, data[i]);
							matched = true;
						}
					}
					if (! matched) {
						exitSet = exitSet.add(self);
					}
					return matched;
				}), {
					// creates and returns a set of new nodes for unmatched data
					enter : function(createFunc) {
						if (! enterData) {
							if (matchKeyFunc) {
								enterData = [];
								$.each(keyedData, function(index, value) {
									if (! usedKeys[index]) {
										enterData.push(value);
									}
								});
							} else {
								enterData = $.grep(data, function(value, index) { 
									return index > maxIndex; 
								});
							}
						}
						if (typeof createFunc === 'string') {
							var template = createFunc;
							createFunc = function(d, i) {
								return $(template);
							};
						}
						if (typeof createFunc === 'function') {
							var newElements = $();
							$.each(enterData, function(index, value) {
								var newElement = createFunc(value, index);
								newElement.data(key, value);
								newElements = newElements.add(newElement);
							});
							return newElements;
						}
						
						return enterData;
					},
					
					// returns the set of existing nodes without matching data
					exit : function() {
						return exitSet;
					}
				});
		}
})(jQuery);

/*
Data Inheritance -- jQuery plugin v1.0
http://jaredbarnes.com/
Copyright 2011, Jared J. Barnes

The MIT License

Copyright (c) 2011, Jared J. Barnes

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

 */
(function(){    
    jQuery.fn.inherit = function(key, args){
        var value, p, x, length = this.length;
        for (x = 0 ; x < length ; x++){
            if(!(value = $(this[x]).data(key)) ){
                p = this[x].parentNode;
                if (p){
                    value = $(p).inherit(key, args);
                    if (typeof value === 'function') {
                        return value(args);
                    } else if (value){
                        return value;
                    }
                }
            } else {
                if(typeof value === 'function') {
                    return value(args);
                } else {
                    return value;
                }
            }
        }
        return undefined;
    };
 
    jQuery.fn.canConfer = function(key){
        var x, i, length = this.length;
        for (x = 0 ; x < length ; x++){
            var p;
 
            if($(this[x]).data(key) ){
                return this;
            }else{
                if (!(p = this[x].parentNode)){
                    return undefined;
                } else {
                    return $(p).canConfer(key);
                }
            }
        }
    };
 
    jQuery.fn.confer = function(key, value, thisElem){    
        var x, i, length = this.length;
 
            for (x = 0 ; x < length ; x++){
              if (!thisElem){
                i = $(this[x]).canConfer(key);
 
                if(!i){
                    $(this[x]).data(key, value);
                } else {
                    $(i).data(key, value);
                }
              } else {
                $(this[x]).data(key, value);
              }
            }
        
        return this;
    };
})();