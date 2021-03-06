import org.apache.commons.lang3.builder.HashCodeBuilder;

public class UmlsQueryResult {

	String cui;
	String term;
	String semantic;
	String preferTerm;
	Integer repeat;
	Float score;
	String source;
	
	public String getCui() {
		return cui;
	}
	public void setCui(String cui) {
		this.cui = cui;
	}
	public String getTerm() {
		return term;
	}
	public void setTerm(String term) {
		this.term = term;
	}
	public String getSemantic() {
		return semantic;
	}
	public void setSemantic(String semantic) {
		this.semantic = semantic;
	}
	public String getPreferTerm() {
		return preferTerm;
	}
	public void setPreferTerm(String preferTerm) {
		this.preferTerm = preferTerm;
	}
	public Integer getRepeat() {
		return repeat;
	}
	public void setRepeat(Integer repeat) {
		this.repeat = repeat;
	}
	public Float getScore() {
		return score;
	}
	public void setScore(Float score) {
		this.score = score;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	
	public String toString() {
		String s = cui + "||" + preferTerm + "||" + term + "||" + source + "||"
				+ semantic + "||" + score;
		return s;
	}
	
	
	public int hashCode() {
        int result = 17;
        result = 31 * result + cui.hashCode();
        result = 31 * result + term.hashCode();
        return result;
    }
	
	public boolean equals(Object q) {
		if (q == this) {
            return true;
        }
        if (q == null || q.getClass() != this.getClass()) {
            return false;
        }
        return (q instanceof UmlsQueryResult) && 
        		(((UmlsQueryResult) q).getTerm()).equals(this.getTerm());
    		
	}

	
	
}
